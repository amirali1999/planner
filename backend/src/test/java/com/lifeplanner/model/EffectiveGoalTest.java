package com.lifeplanner.model;

import com.lifeplanner.model.enums.EffectiveGoalSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveGoalTest {

    @Test
    void neitherRelation() {
        assertThat(EffectiveGoal.resolve(null, null)).isEqualTo(new EffectiveGoal(null, null, false));
    }

    @Test
    void directOnly() {
        assertThat(EffectiveGoal.resolve("A", null)).isEqualTo(new EffectiveGoal("A", EffectiveGoalSource.DIRECT, false));
    }

    @Test
    void focusItemOnly() {
        assertThat(EffectiveGoal.resolve(null, "B")).isEqualTo(new EffectiveGoal("B", EffectiveGoalSource.FOCUS_ITEM, false));
    }

    @Test
    void bothSameGoalIsDirectWithoutWarning() {
        assertThat(EffectiveGoal.resolve("A", "A")).isEqualTo(new EffectiveGoal("A", EffectiveGoalSource.DIRECT, false));
    }

    @Test
    void bothDifferentDirectWinsWithWarning() {
        assertThat(EffectiveGoal.resolve("A", "B")).isEqualTo(new EffectiveGoal("A", EffectiveGoalSource.DIRECT, true));
    }
}
