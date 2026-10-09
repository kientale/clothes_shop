package lemonadex.project.clothes.features.catalog.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class SizeChartDtos {
    private SizeChartDtos() {}

    /**
     * A product's size chart: column headings (the first is usually "Size") and one row per size, each with as many
     * cells as there are columns.
     */
    public record SizeChart(
            @NotNull @Size(min = 2, max = 8) List<@NotBlank @Size(max = 40) String> columns,
            @NotNull @Size(min = 1, max = 30) List<@NotNull @Size(min = 1, max = 8) List<@NotNull @Size(max = 40) String>> rows,
            @Size(max = 500) String note) {}
}
