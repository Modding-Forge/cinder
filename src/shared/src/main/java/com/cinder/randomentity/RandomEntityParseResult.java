package com.cinder.randomentity;

import java.util.List;

/** Parse result with isolated errors for Random Entity resources. */
public record RandomEntityParseResult(RandomEntityRuleSet ruleSet,
                                      List<String> errors) {
    public RandomEntityParseResult {
        errors = List.copyOf(errors);
    }
}
