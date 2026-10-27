package org.soa8.freight.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemPlacementDtoStackingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("isStackable() returns false for null stackable")
    void testIsStackableReturnsFalseForNull() {
        ItemPlacementDto dto = new ItemPlacementDto();
        dto.setStackable(null);

        assertThat(dto.getStackable()).isNull();
        assertThat(dto.isStackable()).isFalse();
    }

    @Test
    @DisplayName("Explicit true remains true")
    void testExplicitTrueRemainsTrue() {
        ItemPlacementDto dto = new ItemPlacementDto();
        dto.setStackable(true);

        assertThat(dto.getStackable()).isTrue();
        assertThat(dto.isStackable()).isTrue();
    }

    @Test
    @DisplayName("Explicit false remains false")
    void testExplicitFalseRemainsFalse() {
        ItemPlacementDto dto = new ItemPlacementDto();
        dto.setStackable(false);

        assertThat(dto.getStackable()).isFalse();
        assertThat(dto.isStackable()).isFalse();
    }

    @Test
    @DisplayName("Legacy 13-parameter constructor does not silently opt into stackability")
    @SuppressWarnings("deprecation")
    void testLegacyConstructorDoesNotOptIntoStackability() {
        ItemPlacementDto dto = new ItemPlacementDto(
                "p1", "it1", 0, "3", "UN1993", 0, 0, 0, 1000, 1000, 1000, 500, "DEFAULT"
        );

        assertThat(dto.getStackable()).isEqualTo(Boolean.FALSE);
        assertThat(dto.isStackable()).isFalse();
    }

    @Test
    @DisplayName("JSON missing the stackable property remains unknown/fail-closed after deserialization")
    void testJsonMissingStackableRemainsNullAndFailClosed() throws Exception {
        String json = """
                {
                    "placementId": "p1",
                    "cargoItemId": "it1",
                    "instanceIndex": 0,
                    "hazardClass": "3",
                    "unNumber": "UN1993",
                    "xMm": 0,
                    "yMm": 0,
                    "zMm": 0,
                    "lengthMm": 1000,
                    "widthMm": 1000,
                    "heightMm": 1000,
                    "weightKg": 500.0,
                    "orientation": "DEFAULT"
                }
                """;

        ItemPlacementDto dto = objectMapper.readValue(json, ItemPlacementDto.class);

        assertThat(dto.getStackable()).isNull();
        assertThat(dto.isStackable()).isFalse();
    }

    @Test
    @DisplayName("JSON containing explicit stackable: true and stackable: false preserves those values")
    void testJsonExplicitStackablePreserved() throws Exception {
        String jsonTrue = """
                {
                    "placementId": "p-true",
                    "stackable": true,
                    "maxStackWeightKg": 800.0
                }
                """;
        ItemPlacementDto dtoTrue = objectMapper.readValue(jsonTrue, ItemPlacementDto.class);
        assertThat(dtoTrue.getStackable()).isTrue();
        assertThat(dtoTrue.isStackable()).isTrue();
        assertThat(dtoTrue.getMaxStackWeightKg()).isEqualTo(800.0);

        String jsonFalse = """
                {
                    "placementId": "p-false",
                    "stackable": false,
                    "maxStackWeightKg": 0.0
                }
                """;
        ItemPlacementDto dtoFalse = objectMapper.readValue(jsonFalse, ItemPlacementDto.class);
        assertThat(dtoFalse.getStackable()).isFalse();
        assertThat(dtoFalse.isStackable()).isFalse();
        assertThat(dtoFalse.getMaxStackWeightKg()).isEqualTo(0.0);
    }
}
