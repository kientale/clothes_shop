package lemonadex.project.clothes.features.catalog.service;

import lemonadex.project.clothes.common.exception.BadRequestException;
import lemonadex.project.clothes.common.exception.ResourceNotFoundException;
import lemonadex.project.clothes.features.catalog.dto.SizeChartDtos.SizeChart;
import lemonadex.project.clothes.features.catalog.repository.CatalogWriteRepository;
import lemonadex.project.clothes.features.catalog.repository.ProductRepository;
import lemonadex.project.clothes.features.catalog.repository.SizeChartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;

/** The size chart shown on a product page ("Bảng size"). */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class SizeChartService {
    private final SizeChartRepository charts;
    private final ProductRepository products;
    private final CatalogWriteRepository writes;
    private final ObjectMapper json;

    /** The chart, or null when the product has none. */
    public SizeChart get(UUID productId) {
        requireProduct(productId);
        return charts.find(productId).map(chart -> json.readValue(chart, SizeChart.class)).orElse(null);
    }

    @Transactional
    public SizeChart save(UUID productId, SizeChart chart) {
        writes.lock();
        requireProduct(productId);
        if (chart.rows().stream().anyMatch(row -> row.size() != chart.columns().size()))
            throw new BadRequestException("SIZE_CHART_SHAPE", "Every row needs one cell per column");
        SizeChart clean = new SizeChart(chart.columns().stream().map(String::strip).toList(),
                chart.rows().stream().map(row -> row.stream().map(String::strip).toList()).toList(),
                chart.note() == null || chart.note().isBlank() ? null : chart.note().strip());
        charts.save(productId, json.writeValueAsString(clean));
        return clean;
    }

    @Transactional
    public void delete(UUID productId) {
        writes.lock();
        requireProduct(productId);
        charts.delete(productId);
    }

    private void requireProduct(UUID productId) {
        if (!products.existsById(productId)) throw new ResourceNotFoundException("Product");
    }
}
