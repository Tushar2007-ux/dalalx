package com.dalalx.core.bots;

/** Whether the runner is letting a bot trade. */
public enum BotStatus {
    /** Trading on its schedule. */
    ACTIVE,
    /** Switched off; its resting orders were withdrawn. */
    PAUSED
}
