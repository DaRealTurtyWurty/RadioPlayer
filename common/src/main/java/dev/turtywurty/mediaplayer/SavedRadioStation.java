package dev.turtywurty.mediaplayer;

public record SavedRadioStation(String nickname, String url) {
    public static SavedRadioStation of(String nickname, String url) {
        String trimmedUrl = url == null ? "" : url.trim();
        String trimmedNickname = nickname == null ? "" : nickname.trim();
        return new SavedRadioStation(trimmedNickname.isBlank() ? trimmedUrl : trimmedNickname, trimmedUrl);
    }
}
