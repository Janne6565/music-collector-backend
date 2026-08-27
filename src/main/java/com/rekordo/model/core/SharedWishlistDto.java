package com.rekordo.model.core;

import java.util.List;

public record SharedWishlistDto(List<SharedWishDto> wishes, long total, boolean truncated) {}
