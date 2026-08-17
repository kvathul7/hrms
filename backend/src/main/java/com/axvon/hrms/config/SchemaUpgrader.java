package com.axvon.hrms.config;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.*;
// Hibernate's ddl-auto=update only ADDS tables and columns. It never relaxes an
// existing NOT NULL constraint, so a database created before the attendance roster
// existed still has attendance.employee_id NOT NULL and would reject every
// roster-only attendance row with a constraint violation.
//
// One idempotent ALTER fixes those installs. On a fresh database Hibernate already
// creates the column nullable and this is a no-op.
@Component @Slf4j
public class SchemaUpgrader {
    private final DataSource dataSource;

    // EntityManagerFactory is injected purely for ordering: depending on it guarantees
    // Hibernate has finished its schema update before we touch the table. Without it
    // this could run against a database where "attendance" does not exist yet.
    public SchemaUpgrader(DataSource dataSource, EntityManagerFactory emf){
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void upgrade(){
        run("ALTER TABLE attendance ALTER COLUMN employee_id SET NULL",
            "attendance.employee_id is now nullable (roster attendance enabled)");
    }

    private void run(String sql, String okMessage){
        try(Connection c = dataSource.getConnection(); Statement s = c.createStatement()){
            s.execute(sql);
            log.info("AXVON schema: {}", okMessage);
        }catch(SQLException e){
            // Already nullable, or the table does not exist yet on a brand-new database.
            // Neither is fatal — the app must still start.
            log.debug("AXVON schema: skipped [{}] — {}", sql, e.getMessage());
        }
    }
}
