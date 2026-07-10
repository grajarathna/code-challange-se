package com.example.store.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that all Liquibase changesets apply cleanly to a fresh PostgreSQL instance and produce the
 * expected schema, including tables, constraints, indexes, and extensions.
 */
@SpringBootTest
class LiquibaseMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_allLiquibaseChangesetsAppliedSuccessfully() {
        // If the Spring context loads without errors, all Liquibase changesets applied successfully.
        // This test explicitly asserts the DataSource is available.
        assertThat(dataSource).isNotNull();
    }

    @Test
    void expectedTablesExist() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            Set<String> tables = getTableNames(metaData);

            assertThat(tables).contains("customer", "purchase_order", "product", "order_product");
        }
    }

    @Test
    void foreignKeyConstraints_orderToCustomer() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // purchase_order should have FK to customer
            List<ForeignKey> fks = getImportedKeys(metaData, "purchase_order");
            assertThat(fks)
                    .anyMatch(fk -> fk.pkTable.equals("customer")
                            && fk.fkColumn.equals("customer_id")
                            && fk.pkColumn.equals("id"));
        }
    }

    @Test
    void foreignKeyConstraints_orderProductToOrder() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // order_product should have FK to purchase_order
            List<ForeignKey> fks = getImportedKeys(metaData, "order_product");
            assertThat(fks)
                    .anyMatch(fk -> fk.pkTable.equals("purchase_order")
                            && fk.fkColumn.equals("order_id")
                            && fk.pkColumn.equals("id"));
        }
    }

    @Test
    void foreignKeyConstraints_orderProductToProduct() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // order_product should have FK to product
            List<ForeignKey> fks = getImportedKeys(metaData, "order_product");
            assertThat(fks)
                    .anyMatch(fk -> fk.pkTable.equals("product")
                            && fk.fkColumn.equals("product_id")
                            && fk.pkColumn.equals("id"));
        }
    }

    @Test
    void uniqueConstraint_customerEmail() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Check that customer.email has a unique index
            Set<String> uniqueColumns = getUniqueIndexColumns(metaData, "customer");
            assertThat(uniqueColumns).contains("email");
        }
    }

    @Test
    void notNullConstraints_customerTable() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            List<ColumnInfo> columns = getColumns(metaData, "customer");
            assertThat(columns).anyMatch(c -> c.name.equals("name") && !c.nullable);
            assertThat(columns).anyMatch(c -> c.name.equals("email") && !c.nullable);
        }
    }

    @Test
    void notNullConstraints_purchaseOrderTable() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            List<ColumnInfo> columns = getColumns(metaData, "purchase_order");
            assertThat(columns).anyMatch(c -> c.name.equals("description") && !c.nullable);
            assertThat(columns).anyMatch(c -> c.name.equals("customer_id") && !c.nullable);
        }
    }

    @Test
    void notNullConstraints_productTable() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            List<ColumnInfo> columns = getColumns(metaData, "product");
            assertThat(columns).anyMatch(c -> c.name.equals("description") && !c.nullable);
        }
    }

    @Test
    void notNullConstraints_orderProductTable() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            List<ColumnInfo> columns = getColumns(metaData, "order_product");
            assertThat(columns).anyMatch(c -> c.name.equals("order_id") && !c.nullable);
            assertThat(columns).anyMatch(c -> c.name.equals("product_id") && !c.nullable);
        }
    }

    @Test
    void expectedIndexesExist() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            Set<String> customerIndexes = getIndexNames(metaData, "customer");
            assertThat(customerIndexes).contains("idx_customer_name_trgm");

            Set<String> orderIndexes = getIndexNames(metaData, "purchase_order");
            assertThat(orderIndexes).contains("idx_purchase_order_customer_id");
        }
    }

    @Test
    void pgTrgmExtensionIsEnabled() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm'")) {
            assertThat(rs.next()).as("pg_trgm extension should be enabled").isTrue();
        }
    }

    // ========== Helper methods ==========

    private Set<String> getTableNames(DatabaseMetaData metaData) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (ResultSet rs = metaData.getTables(null, "public", null, new String[] {"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private List<ForeignKey> getImportedKeys(DatabaseMetaData metaData, String tableName) throws SQLException {
        List<ForeignKey> keys = new ArrayList<>();
        try (ResultSet rs = metaData.getImportedKeys(null, "public", tableName)) {
            while (rs.next()) {
                keys.add(new ForeignKey(
                        rs.getString("PKTABLE_NAME"),
                        rs.getString("PKCOLUMN_NAME"),
                        rs.getString("FKTABLE_NAME"),
                        rs.getString("FKCOLUMN_NAME")));
            }
        }
        return keys;
    }

    private Set<String> getUniqueIndexColumns(DatabaseMetaData metaData, String tableName) throws SQLException {
        Set<String> uniqueColumns = new HashSet<>();
        try (ResultSet rs = metaData.getIndexInfo(null, "public", tableName, true, false)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                if (columnName != null) {
                    uniqueColumns.add(columnName);
                }
            }
        }
        return uniqueColumns;
    }

    private List<ColumnInfo> getColumns(DatabaseMetaData metaData, String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(null, "public", tableName, null)) {
            while (rs.next()) {
                columns.add(new ColumnInfo(
                        rs.getString("COLUMN_NAME"),
                        rs.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls ? false : true));
            }
        }
        return columns;
    }

    private Set<String> getIndexNames(DatabaseMetaData metaData, String tableName) throws SQLException {
        Set<String> indexes = new HashSet<>();
        try (ResultSet rs = metaData.getIndexInfo(null, "public", tableName, false, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName != null) {
                    indexes.add(indexName);
                }
            }
        }
        return indexes;
    }

    private record ForeignKey(String pkTable, String pkColumn, String fkTable, String fkColumn) {}

    private record ColumnInfo(String name, boolean nullable) {}
}
