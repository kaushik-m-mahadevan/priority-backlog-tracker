package com.backlogtracker.materialinventory.yarn.dto;

public record CreateYarnTypeRequest(String brand, String thickness, String colour, String material,
                                    Double skeinWeightGrams, Double skeinLengthMeters, String recommendedHookSize,
                                    String notes, Double costPerSkein) {
}
