package com.zaremate.punish.data;

import java.util.List;

public record Offense(String id, String group, String name, List<String> steps, List<String> aliases) {}