package com.contextextractor.application;

import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.domain.model.ContextPayload;
import com.contextextractor.domain.model.DatabaseConfig;
import com.contextextractor.domain.model.TableConfig;
import com.contextextractor.domain.strategy.ContextExportStrategy;
import com.contextextractor.domain.strategy.DatabaseInspectorStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenerateContextUseCaseTest {

    private DatabaseInspectorStrategy dbInspector;
    private ContextExportStrategy exporter;
    private GenerateContextUseCase useCase;
    private final List<GenerateContextUseCase.GenerationProgress> progressUpdates = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dbInspector = mock(DatabaseInspectorStrategy.class);
        exporter = mock(ContextExportStrategy.class);
        useCase = new GenerateContextUseCase(dbInspector, exporter);
        progressUpdates.clear();
    }

    @Test
    @DisplayName("Calls progressCallback with increasing progress values throughout execution")
    void callsProgressCallbackWithIncreasingValues() throws Exception {
        ContextPayload payload = buildPayloadWithoutDb();
        Path outputFile = Path.of("output.xml");
        when(exporter.export(any(), any())).thenReturn(List.of(outputFile));

        useCase.execute(payload, progressUpdates::add);

        assertTrue(progressUpdates.size() >= 3);
        for (int i = 1; i < progressUpdates.size(); i++) {
            assertTrue(progressUpdates.get(i).progress() >= progressUpdates.get(i - 1).progress());
        }
        assertEquals(1.0, progressUpdates.get(progressUpdates.size() - 1).progress());
    }

    @Test
    @DisplayName("Does not call dbInspector when databaseConfig is null")
    void doesNotCallDbInspectorWhenConfigIsNull() throws Exception {
        ContextPayload payload = buildPayloadWithoutDb();
        when(exporter.export(any(), any())).thenReturn(List.of(Path.of("out.xml")));

        useCase.execute(payload, progressUpdates::add);

        verifyNoInteractions(dbInspector);
    }

    @Test
    @DisplayName("Does not call dbInspector when databaseConfig host is blank")
    void doesNotCallDbInspectorWhenHostIsBlank() throws Exception {
        DatabaseConfig blankHost = new DatabaseConfig("", 5432, "db", "user", "pass", "public");
        ContextPayload payload = new ContextPayload(
                "", Map.of(), blankHost, List.of(), Map.of(), Map.of(),
                "", AppSettings.defaults(), null, "out", List.of());
        when(exporter.export(any(), any())).thenReturn(List.of(Path.of("out.xml")));

        useCase.execute(payload, progressUpdates::add);

        verifyNoInteractions(dbInspector);
    }

    @Test
    @DisplayName("Does not call dbInspector when tables list is empty")
    void doesNotCallDbInspectorWhenTablesEmpty() throws Exception {
        DatabaseConfig config = new DatabaseConfig("localhost", 5432, "db", "user", "pass", "public");
        ContextPayload payload = new ContextPayload(
                "", Map.of(), config, List.of(), Map.of(), Map.of(),
                "", AppSettings.defaults(), null, "out", List.of());
        when(exporter.export(any(), any())).thenReturn(List.of(Path.of("out.xml")));

        useCase.execute(payload, progressUpdates::add);

        verifyNoInteractions(dbInspector);
    }

    @Test
    @DisplayName("Fetches DDL only for tables with exportDdl set to true")
    void fetchesDdlOnlyForExportDdlTables() throws Exception {
        DatabaseConfig config = new DatabaseConfig("localhost", 5432, "db", "user", "pass", "public");
        TableConfig ddlTable = new TableConfig("users", true, false, 5, "", "");
        TableConfig noDdlTable = new TableConfig("logs", false, false, 5, "", "");
        ContextPayload payload = new ContextPayload(
                "", Map.of(), config, List.of(ddlTable, noDdlTable), Map.of(), Map.of(),
                "", AppSettings.defaults(), null, "out", List.of());

        when(dbInspector.fetchDdl(any(), anyString(), eq("users"))).thenReturn("CREATE TABLE users();");
        when(exporter.export(any(), any())).thenReturn(List.of(Path.of("out.xml")));

        useCase.execute(payload, progressUpdates::add);

        verify(dbInspector).fetchDdl(any(), anyString(), eq("users"));
        verify(dbInspector, never()).fetchDdl(any(), anyString(), eq("logs"));
    }

    @Test
    @DisplayName("Fetches data only for tables with exportData set to true")
    void fetchesDataOnlyForExportDataTables() throws Exception {
        DatabaseConfig config = new DatabaseConfig("localhost", 5432, "db", "user", "pass", "public");
        TableConfig dataTable = new TableConfig("orders", false, true, 10, "", "");
        TableConfig noDataTable = new TableConfig("audit", false, false, 5, "", "");
        ContextPayload payload = new ContextPayload(
                "", Map.of(), config, List.of(dataTable, noDataTable), Map.of(), Map.of(),
                "", AppSettings.defaults(), null, "out", List.of());

        when(dbInspector.fetchData(any(), eq(dataTable))).thenReturn(List.of(Map.of("id", "1")));
        when(exporter.export(any(), any())).thenReturn(List.of(Path.of("out.xml")));

        useCase.execute(payload, progressUpdates::add);

        verify(dbInspector).fetchData(any(), eq(dataTable));
        verify(dbInspector, never()).fetchData(any(), eq(noDataTable));
    }

    @Test
    @DisplayName("""
            Given a table with exportDdl true and exportData false,
            when execute is called,
            then fetchDdl is called and fetchData is not called for that table
            """)
    void fetchesDdlButNotDataWhenOnlyDdlEnabled() throws Exception {
        DatabaseConfig config = new DatabaseConfig("localhost", 5432, "db", "user", "pass", "public");
        TableConfig table = new TableConfig("products", true, false, 5, "", "");
        ContextPayload payload = new ContextPayload(
                "", Map.of(), config, List.of(table), Map.of(), Map.of(),
                "", AppSettings.defaults(), null, "out", List.of());

        when(dbInspector.fetchDdl(any(), anyString(), eq("products"))).thenReturn("CREATE TABLE products();");
        when(exporter.export(any(), any())).thenReturn(List.of(Path.of("out.xml")));

        useCase.execute(payload, progressUpdates::add);

        verify(dbInspector).fetchDdl(any(), anyString(), eq("products"));
        verify(dbInspector, never()).fetchData(any(), any());
    }

    @Test
    @DisplayName("Returns the output file paths from the export strategy")
    void returnsOutputFilePathsFromExporter() throws Exception {
        ContextPayload payload = buildPayloadWithoutDb();
        List<Path> expectedFiles = List.of(Path.of("context.xml"), Path.of("context-part2.xml"));
        when(exporter.export(any(), any())).thenReturn(expectedFiles);

        GenerateContextUseCase.GenerationResult result = useCase.execute(payload, progressUpdates::add);

        assertEquals(expectedFiles, result.outputFiles());
    }

    @Test
    @DisplayName("Enriched payload passed to exporter contains fetched DDL and data")
    void enrichedPayloadContainsFetchedDdlAndData() throws Exception {
        DatabaseConfig config = new DatabaseConfig("localhost", 5432, "db", "user", "pass", "public");
        TableConfig table = new TableConfig("users", true, true, 5, "", "");
        ContextPayload payload = new ContextPayload(
                "agent", Map.of("f.txt", "content"), config, List.of(table), Map.of(), Map.of(),
                "notes", AppSettings.defaults(), null, "out", List.of());

        String expectedDdl = "CREATE TABLE users (id INT);";
        List<Map<String, String>> expectedRows = List.of(Map.of("id", "1"));
        when(dbInspector.fetchDdl(any(), anyString(), eq("users"))).thenReturn(expectedDdl);
        when(dbInspector.fetchData(any(), eq(table))).thenReturn(expectedRows);
        when(exporter.export(any(), any())).thenReturn(List.of(Path.of("out.xml")));

        useCase.execute(payload, progressUpdates::add);

        ArgumentCaptor<ContextPayload> captor = ArgumentCaptor.forClass(ContextPayload.class);
        verify(exporter).export(captor.capture(), any());
        ContextPayload enriched = captor.getValue();

        assertEquals(expectedDdl, enriched.tableDdl().get("users"));
        assertEquals(expectedRows, enriched.tableData().get("users"));
    }

    @Test
    @DisplayName("Constructor rejects null dbInspector")
    void rejectsNullDbInspector() {
        assertThrows(NullPointerException.class, () -> new GenerateContextUseCase(null, exporter));
    }

    @Test
    @DisplayName("Constructor rejects null exporter")
    void rejectsNullExporter() {
        assertThrows(NullPointerException.class, () -> new GenerateContextUseCase(dbInspector, null));
    }

    private ContextPayload buildPayloadWithoutDb() {
        return new ContextPayload(
                "", Map.of("test.txt", "content"), null, List.of(), Map.of(), Map.of(),
                "", AppSettings.defaults(), null, "output", List.of());
    }
}
