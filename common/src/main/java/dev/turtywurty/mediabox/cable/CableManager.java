package dev.turtywurty.mediabox.cable;

import dev.turtywurty.mediabox.cable.concealed.ConcealedCableRun;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Owns visible cable connections and maintains their derived network topology.
 *
 * <p>This class is intended to be used from the server thread. Its collection accessors return
 * immutable snapshots, so callers cannot accidentally invalidate its indexes.</p>
 */
public final class CableManager {
    private final Map<UUID, VisibleCableConnection> visibleConnections = new LinkedHashMap<>();
    private final Map<PortEndpoint, Set<UUID>> connectionsByEndpoint = new HashMap<>();

    private final Map<UUID, ConcealedCableRun> concealedRuns = new LinkedHashMap<>();
    private final Map<PortEndpoint, Set<UUID>> concealedRunsByTerminal = new HashMap<>();

    private final Map<PortSignalKey, CableNetwork> networkByPortAndSignal = new HashMap<>();

    public void addVisibleCable(VisibleCableConnection cable) {
        validateCable(cable);

        VisibleCableConnection existing = this.visibleConnections.get(cable.id());
        if (existing != null) {
            if (existing.equals(cable))
                return;

            throw new IllegalArgumentException("A visible cable with ID " + cable.id() + " already exists");
        }

        visibleConnections.put(cable.id(), cable);

        connectionsByEndpoint
                .computeIfAbsent(cable.first(), ignored -> new HashSet<>())
                .add(cable.id());

        connectionsByEndpoint
                .computeIfAbsent(cable.second(), ignored -> new HashSet<>())
                .add(cable.id());

        rebuildNetworks();
    }

    public void addConcealedCableRun(ConcealedCableRun run) {
        Objects.requireNonNull(run, "run");

        ConcealedCableRun existing = this.concealedRuns.get(run.id());
        if (existing != null) {
            if (existing.equals(run))
                return;

            throw new IllegalArgumentException("A concealed cable run with ID " + run.id() + " already exists");
        }

        validateConcealedRun(run);

        this.concealedRuns.put(run.id(), run);
        for (PortEndpoint terminal : run.terminals()) {
            this.concealedRunsByTerminal
                    .computeIfAbsent(terminal, ignored -> new HashSet<>())
                    .add(run.id());
        }

        rebuildNetworks();
    }

    public void updateVisibleCable(VisibleCableConnection cable) {
        validateCable(cable);
        VisibleCableConnection previous = this.visibleConnections.get(cable.id());
        if (previous == null)
            throw new IllegalArgumentException("No visible cable with ID " + cable.id() + " exists");
        if (previous.equals(cable))
            return;
        if (!previous.first().equals(cable.first())
                || !previous.second().equals(cable.second())
                || previous.signalType() != cable.signalType())
            throw new IllegalArgumentException("A route update cannot change visible cable topology");

        this.visibleConnections.put(cable.id(), cable);
    }

    public void updateConcealedCableRun(ConcealedCableRun run) {
        Objects.requireNonNull(run, "run");
        ConcealedCableRun previous = this.concealedRuns.get(run.id());
        if (previous == null) {
            addConcealedCableRun(run);
            return;
        }

        if (previous.equals(run))
            return;

        validateConcealedRun(run);
        for (PortEndpoint oldTerminal : previous.terminals()) {
            removeConcealedTerminalIndex(oldTerminal, run.id());
        }

        this.concealedRuns.put(run.id(), run);
        for (PortEndpoint terminal : run.terminals()) {
            this.concealedRunsByTerminal
                    .computeIfAbsent(terminal, ignored -> new HashSet<>())
                    .add(run.id());
        }

        rebuildNetworks();
    }

    public Optional<VisibleCableConnection> removeVisibleCable(UUID cableId) {
        Objects.requireNonNull(cableId, "cableId");

        VisibleCableConnection removed = this.visibleConnections.remove(cableId);
        if (removed == null)
            return Optional.empty();

        removeEndpointIndex(removed.first(), cableId);
        removeEndpointIndex(removed.second(), cableId);
        rebuildNetworks();
        return Optional.of(removed);
    }

    public boolean removeVisibleCable(VisibleCableConnection cable) {
        Objects.requireNonNull(cable, "cable");

        VisibleCableConnection existing = this.visibleConnections.get(cable.id());
        if (!cable.equals(existing))
            return false;

        removeVisibleCable(cable.id());
        return true;
    }

    public Optional<ConcealedCableRun> removeConcealedCableRun(UUID runId) {
        Objects.requireNonNull(runId, "runId");
        ConcealedCableRun removed = this.concealedRuns.remove(runId);
        if (removed == null)
            return Optional.empty();

        for (PortEndpoint terminal : removed.terminals()) {
            removeConcealedTerminalIndex(terminal, runId);
        }

        rebuildNetworks();
        return Optional.of(removed);
    }

    public Optional<VisibleCableConnection> visibleCable(UUID cableId) {
        Objects.requireNonNull(cableId, "cableId");
        return Optional.ofNullable(this.visibleConnections.get(cableId));
    }

    public Map<UUID, VisibleCableConnection> visibleConnections() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.visibleConnections));
    }

    public Optional<ConcealedCableRun> concealedCableRun(UUID runId) {
        Objects.requireNonNull(runId, "runId");
        return Optional.ofNullable(this.concealedRuns.get(runId));
    }

    public Set<ConcealedCableRun> concealedCableRunsAt(PortEndpoint terminal) {
        Objects.requireNonNull(terminal, "terminal");
        Set<ConcealedCableRun> runs = new LinkedHashSet<>();
        for (UUID runId : this.concealedRunsByTerminal.getOrDefault(terminal, Set.of())) {
            ConcealedCableRun run = this.concealedRuns.get(runId);
            if (run != null)
                runs.add(run);
        }

        return Set.copyOf(runs);
    }

    public Map<UUID, ConcealedCableRun> concealedCableRuns() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.concealedRuns));
    }

    public Set<UUID> connectionIdsAt(PortEndpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        return Set.copyOf(this.connectionsByEndpoint.getOrDefault(endpoint, Set.of()));
    }

    public int connectionCount(PortEndpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        return this.connectionsByEndpoint.getOrDefault(endpoint, Set.of()).size()
                + this.concealedRunsByTerminal.getOrDefault(endpoint, Set.of()).size();
    }

    public Set<VisibleCableConnection> visibleCablesAt(PortEndpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");

        Set<VisibleCableConnection> cables = new LinkedHashSet<>();
        for (UUID cableId : this.connectionsByEndpoint.getOrDefault(endpoint, Set.of())) {
            VisibleCableConnection cable = this.visibleConnections.get(cableId);
            if (cable != null) {
                cables.add(cable);
            }
        }

        return Set.copyOf(cables);
    }

    public Optional<CableNetwork> networkAt(PortEndpoint endpoint, MediaSignalType signalType) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(signalType, "signalType");
        return Optional.ofNullable(this.networkByPortAndSignal.get(new PortSignalKey(endpoint, signalType)));
    }

    public Set<CableNetwork> networks() {
        return Set.copyOf(this.networkByPortAndSignal.values());
    }

    public int visibleCableCount() {
        return this.visibleConnections.size();
    }

    public int networkCount() {
        return networks().size();
    }

    public int concealedCableRunCount() {
        return this.concealedRuns.size();
    }

    public boolean isEmpty() {
        return this.visibleConnections.isEmpty() && this.concealedRuns.isEmpty();
    }

    public void clear() {
        this.visibleConnections.clear();
        this.connectionsByEndpoint.clear();
        this.concealedRuns.clear();
        this.concealedRunsByTerminal.clear();
        this.networkByPortAndSignal.clear();
    }

    private static void validateCable(VisibleCableConnection cable) {
        Objects.requireNonNull(cable, "cable");
        Objects.requireNonNull(cable.id(), "cable.id");
        validateEndpoint(cable.first(), "cable.first");
        validateEndpoint(cable.second(), "cable.second");
        Objects.requireNonNull(cable.signalType(), "cable.signalType");

        if (cable.first().equals(cable.second()))
            throw new IllegalArgumentException("A visible cable must connect two different ports");

        if (!cable.first().dimension().equals(cable.second().dimension()))
            throw new IllegalArgumentException("A visible cable cannot connect ports in different dimensions");

    }

    private static void validateEndpoint(PortEndpoint endpoint, String name) {
        Objects.requireNonNull(endpoint, name);
        Objects.requireNonNull(endpoint.dimension(), name + ".dimension");
        Objects.requireNonNull(endpoint.pos(), name + ".pos");
        Objects.requireNonNull(endpoint.portId(), name + ".portId");
    }

    private static void validateConcealedRun(ConcealedCableRun run) {
        Objects.requireNonNull(run, "run");
        ResourceKey<Level> dimension = null;

        for (PortEndpoint terminal : run.terminals()) {
            validateEndpoint(terminal, "run.terminal");
            if (dimension == null)
                dimension = terminal.dimension();
            else if (!dimension.equals(terminal.dimension()))
                throw new IllegalArgumentException("A concealed cable run cannot span dimensions");
        }
    }

    private void removeEndpointIndex(PortEndpoint endpoint, UUID cableId) {
        Set<UUID> cableIds = this.connectionsByEndpoint.get(endpoint);
        if (cableIds == null)
            return;

        cableIds.remove(cableId);
        if (cableIds.isEmpty()) {
            this.connectionsByEndpoint.remove(endpoint);
        }
    }

    private void removeConcealedTerminalIndex(PortEndpoint endpoint, UUID runId) {
        Set<UUID> runIds = this.concealedRunsByTerminal.get(endpoint);
        if (runIds == null)
            return;

        runIds.remove(runId);
        if (runIds.isEmpty())
            this.concealedRunsByTerminal.remove(endpoint);
    }

    private void rebuildNetworks() {
        Collection<CableNetwork> oldNetworks = new HashSet<>(this.networkByPortAndSignal.values());
        List<NetworkComponent> components = findNetworkComponents();
        Map<Integer, UUID> reusableIds = matchReusableNetworkIds(components, oldNetworks);

        this.networkByPortAndSignal.clear();
        Set<UUID> usedIds = new HashSet<>(reusableIds.values());

        for (int index = 0; index < components.size(); index++) {
            NetworkComponent component = components.get(index);
            UUID networkId = reusableIds.get(index);
            if (networkId == null) {
                do {
                    networkId = UUID.randomUUID();
                } while (!usedIds.add(networkId));
            }

            CableNetwork network = new CableNetwork(networkId, component.ports(), component.signalType());
            for (PortEndpoint port : component.ports()) {
                this.networkByPortAndSignal.put(new PortSignalKey(port, component.signalType()), network);
            }
        }
    }

    private List<NetworkComponent> findNetworkComponents() {
        List<NetworkComponent> components = new ArrayList<>();
        Set<PortSignalKey> visited = new HashSet<>();

        Set<PortSignalKey> startingPorts = new LinkedHashSet<>();
        for (VisibleCableConnection cable : this.visibleConnections.values()) {
            startingPorts.add(new PortSignalKey(cable.first(), cable.signalType()));
            startingPorts.add(new PortSignalKey(cable.second(), cable.signalType()));
        }

        for (ConcealedCableRun run : this.concealedRuns.values()) {
            for (PortEndpoint terminal : run.terminals()) {
                startingPorts.add(new PortSignalKey(terminal, run.signalType()));
            }
        }

        for (PortSignalKey startingPort : startingPorts) {
            if (visited.contains(startingPort))
                continue;

            Set<PortEndpoint> ports = new LinkedHashSet<>();
            ArrayDeque<PortEndpoint> pending = new ArrayDeque<>();
            pending.add(startingPort.endpoint());

            while (!pending.isEmpty()) {
                PortEndpoint endpoint = pending.removeFirst();
                if (!visited.add(new PortSignalKey(endpoint, startingPort.signalType())))
                    continue;

                ports.add(endpoint);
                for (PortEndpoint adjacent : adjacentPorts(endpoint, startingPort.signalType())) {
                    if (!visited.contains(new PortSignalKey(adjacent, startingPort.signalType()))) {
                        pending.addLast(adjacent);
                    }
                }
            }

            components.add(new NetworkComponent(startingPort.signalType(), ports));
        }

        return components;
    }

    private Set<PortEndpoint> adjacentPorts(PortEndpoint endpoint, MediaSignalType signalType) {
        Set<PortEndpoint> adjacent = new LinkedHashSet<>();

        for (UUID cableId : this.connectionsByEndpoint.getOrDefault(endpoint, Set.of())) {
            VisibleCableConnection cable = this.visibleConnections.get(cableId);
            if (cable == null || cable.signalType() != signalType)
                continue;

            adjacent.add(cable.first().equals(endpoint) ? cable.second() : cable.first());
        }

        for (UUID runId : this.concealedRunsByTerminal.getOrDefault(endpoint, Set.of())) {
            ConcealedCableRun run = this.concealedRuns.get(runId);
            if (run != null && run.signalType() == signalType) {
                for (PortEndpoint terminal : run.terminals()) {
                    if (!terminal.equals(endpoint))
                        adjacent.add(terminal);
                }
            }
        }

        return adjacent;
    }

    private static Map<Integer, UUID> matchReusableNetworkIds(
            List<NetworkComponent> components,
            Collection<CableNetwork> oldNetworks) {
        List<NetworkMatch> matches = new ArrayList<>();

        for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
            NetworkComponent component = components.get(componentIndex);
            for (CableNetwork oldNetwork : oldNetworks) {
                if (component.signalType() != oldNetwork.signalType())
                    continue;

                int overlap = intersectionSize(component.ports(), oldNetwork.ports());
                if (overlap > 0) {
                    matches.add(new NetworkMatch(componentIndex, oldNetwork.id(), overlap));
                }
            }
        }

        matches.sort(Comparator
                .comparingInt(NetworkMatch::overlap).reversed()
                .thenComparing(NetworkMatch::networkId)
                .thenComparingInt(NetworkMatch::componentIndex));

        Map<Integer, UUID> matchedIds = new HashMap<>();
        Set<UUID> claimedIds = new HashSet<>();
        for (NetworkMatch match : matches) {
            if (!matchedIds.containsKey(match.componentIndex()) && claimedIds.add(match.networkId())) {
                matchedIds.put(match.componentIndex(), match.networkId());
            }
        }

        return matchedIds;
    }

    private static int intersectionSize(Set<PortEndpoint> first, Set<PortEndpoint> second) {
        Set<PortEndpoint> smaller = first.size() <= second.size() ? first : second;
        Set<PortEndpoint> larger = smaller == first ? second : first;
        int matches = 0;
        for (PortEndpoint endpoint : smaller) {
            if (larger.contains(endpoint)) {
                matches++;
            }
        }

        return matches;
    }

    private record NetworkComponent(MediaSignalType signalType, Set<PortEndpoint> ports) {
    }

    private record PortSignalKey(PortEndpoint endpoint, MediaSignalType signalType) {
    }

    private record NetworkMatch(int componentIndex, UUID networkId, int overlap) {
    }
}
