package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class V11__allow_multiple_refunds_per_payment extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        dropConstraint(connection, findPaymentConstraint(connection, "FOREIGN KEY"));
        dropConstraint(connection, findPaymentConstraint(connection, "UNIQUE"));
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table refund_order add constraint fk_refund_order_payment " +
                    "foreign key (payment_id) references payment_order(id)");
            statement.execute("create index idx_refund_order_payment_id on refund_order(payment_id)");
        }
    }

    private void dropConstraint(Connection connection, String constraintName) throws Exception {
        if (constraintName == null) return;
        String quote = connection.getMetaData().getIdentifierQuoteString().trim();
        String quotedName = quote + constraintName.replace(quote, quote + quote) + quote;
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table refund_order drop constraint " + quotedName);
        }
    }

    private String findPaymentConstraint(Connection connection, String type) throws Exception {
        String sql = """
                select tc.constraint_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_schema = kcu.constraint_schema
                 and tc.constraint_name = kcu.constraint_name
                 and tc.table_name = kcu.table_name
                where lower(tc.table_name) = 'refund_order'
                  and tc.constraint_type = ?
                group by tc.constraint_name
                having count(*) = 1 and max(lower(kcu.column_name)) = 'payment_id'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            try (ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
            }
        }
    }
}
