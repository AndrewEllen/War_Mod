package com.andye.warmod.antiair;

import java.util.UUID;

/** Live, server-only ownership preference for an interceptor route. */
public record AntiAirTargetClaim(UUID interceptorId, UUID targetId, long claimedGameTime) { }
