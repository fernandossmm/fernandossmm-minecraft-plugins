package me.fernandossm.discordsrvfailclosed;

/** Why logins are refused: each way DiscordSRV's linked-account requirement may not be in force. */
public enum Refusal {
    NOT_INSTALLED("DiscordSRV is not installed"),
    DISABLED("DiscordSRV is not enabled"),
    SETTING_UNREADABLE("DiscordSRV's \"Require linked account to play\" setting cannot be read"),
    LINKING_OFF("DiscordSRV's \"Require linked account to play\" is not enabled"),
    ERROR("the check itself failed");

    private final String text;

    Refusal(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
