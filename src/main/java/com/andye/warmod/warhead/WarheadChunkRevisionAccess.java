package com.andye.warmod.warhead;

public interface WarheadChunkRevisionAccess {
    long war_mod$getChunkRevision();

    long war_mod$getSectionRevision(int sectionY);

    void war_mod$markBulkSectionChanged(int sectionY);
}
