package com.surf.tracker.alerting.domain.parameters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.domain.enums.AlertRuleType;
import com.surf.tracker.main.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleParametersMapperTest {

    private final ObjectMapper httpMapper = new AppConfig().objectMapper();
    private final RuleParametersMapper mapper = new RuleParametersMapper(httpMapper);

    @Test
    void fromMap_storedSpeedParameters_preservesThresholdAndAssignments() {
        // given
        var expected = new SpeedRuleParameters(83.5f, 4.5f, "WARNING",
                List.of("vehicle-001"), "km/h", 12);
        Map<String, Object> stored = mapper.toMap(expected);

        // when
        RuleParameters result = mapper.fromMap(AlertRuleType.SPEED, stored);

        // then
        assertThat(result).isEqualTo(expected);
        assertThat(((SpeedRuleParameters) result).speedLimit()).isNotZero();
    }

    @Test
    void fromMap_storedIdleParameters_preservesThresholdAndAssignments() {
        // given
        var expected = new IdleRuleParameters(27, "WARNING", List.of("vehicle-001"), true, 3);
        Map<String, Object> stored = mapper.toMap(expected);

        // when
        RuleParameters result = mapper.fromMap(AlertRuleType.TIME, stored);

        // then
        assertThat(result).isEqualTo(expected);
        assertThat(((IdleRuleParameters) result).maxIdleMinutes()).isNotZero();
    }

    @Test
    void fromMap_storedGeofenceParameters_preservesIdentityGeometryAndDwellThreshold() {
        // given
        var expected = new GeofenceRuleParameters("42", "Depot", "EXIT", "CRITICAL",
                List.of("vehicle-001"), "CIRCLE", 51.5, -0.12, 450, 18);
        Map<String, Object> stored = mapper.toMap(expected);

        // when
        RuleParameters result = mapper.fromMap(AlertRuleType.GEOFENCE, stored);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void fromMap_unknownRawFields_leavesSourceUntouched() {
        // given
        Map<String, Object> stored = new LinkedHashMap<>(Map.of(
                "speedLimit", 83.5f, "vehicleIds", List.of("vehicle-001"),
                "vendor_extension", Map.of("rawField", List.of(1, 2, 3))));
        Map<String, Object> original = new LinkedHashMap<>(stored);

        // when
        RuleParameters result = mapper.fromMap(AlertRuleType.SPEED, stored);

        // then
        assertThat(stored).isEqualTo(original);
        assertThat(result.vehicleIds()).containsExactly("vehicle-001");
    }

    @Test
    void fromMap_distinctThresholds_doesNotCollapseToPrimitiveDefaults() {
        // given
        Map<String, Object> lower = Map.of("speedLimit", 40, "maxIdleMinutes", 10);
        Map<String, Object> higher = Map.of("speedLimit", 90, "maxIdleMinutes", 30);

        // when
        var lowerSpeed = (SpeedRuleParameters) mapper.fromMap(AlertRuleType.SPEED, lower);
        var higherSpeed = (SpeedRuleParameters) mapper.fromMap(AlertRuleType.SPEED, higher);
        var lowerIdle = (IdleRuleParameters) mapper.fromMap(AlertRuleType.TIME, lower);
        var higherIdle = (IdleRuleParameters) mapper.fromMap(AlertRuleType.TIME, higher);

        // then
        assertThat(lowerSpeed.speedLimit()).isEqualTo(40);
        assertThat(higherSpeed.speedLimit()).isEqualTo(90);
        assertThat(lowerIdle.maxIdleMinutes()).isEqualTo(10);
        assertThat(higherIdle.maxIdleMinutes()).isEqualTo(30);
    }

    @Test
    void fromMap_sharedHttpMapper_preservesSnakeCaseAndRawMapKeys() throws Exception {
        // given
        var parameters = new SpeedRuleParameters(80, 5, "WARNING", List.of("vehicle-001"), "km/h", 10);
        Map<String, Object> stored = mapper.toMap(parameters);

        // when
        mapper.fromMap(AlertRuleType.SPEED, stored);
        var typedJson = httpMapper.readTree(httpMapper.writeValueAsString(parameters));
        var rawJson = httpMapper.readTree(httpMapper.writeValueAsString(stored));

        // then
        assertThat(typedJson.has("speed_limit")).isTrue();
        assertThat(typedJson.has("vehicle_ids")).isTrue();
        assertThat(typedJson.has("speedLimit")).isFalse();
        assertThat(rawJson.has("speedLimit")).isTrue();
        assertThat(rawJson.has("vehicleIds")).isTrue();
    }
}
