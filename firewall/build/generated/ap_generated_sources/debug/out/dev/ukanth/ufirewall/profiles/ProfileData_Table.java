package dev.ukanth.ufirewall.profiles;

import android.content.ContentValues;
import com.raizlabs.android.dbflow.config.DatabaseDefinition;
import com.raizlabs.android.dbflow.sql.QueryBuilder;
import com.raizlabs.android.dbflow.sql.language.OperatorGroup;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.sql.language.property.IProperty;
import com.raizlabs.android.dbflow.sql.language.property.Property;
import com.raizlabs.android.dbflow.sql.saveable.AutoIncrementModelSaver;
import com.raizlabs.android.dbflow.sql.saveable.ModelSaver;
import com.raizlabs.android.dbflow.structure.ModelAdapter;
import com.raizlabs.android.dbflow.structure.database.DatabaseStatement;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.FlowCursor;
import java.lang.Class;
import java.lang.IllegalArgumentException;
import java.lang.Long;
import java.lang.Number;
import java.lang.Override;
import java.lang.String;
import javax.annotation.Generated;

/**
 * This is generated code. Please do not modify */
@Generated("com.raizlabs.android.dbflow.processor.DBFlowProcessor")
public final class ProfileData_Table extends ModelAdapter<ProfileData> {
  /**
   * Primary Key AutoIncrement */
  public static final Property<Long> id = new Property<Long>(ProfileData.class, "id");

  public static final Property<String> name = new Property<String>(ProfileData.class, "name");

  public static final Property<String> identifier = new Property<String>(ProfileData.class, "identifier");

  public static final Property<String> attibutes = new Property<String>(ProfileData.class, "attibutes");

  public static final Property<String> parentProfile = new Property<String>(ProfileData.class, "parentProfile");

  public static final IProperty[] ALL_COLUMN_PROPERTIES = new IProperty[]{id,name,identifier,attibutes,parentProfile};

  public ProfileData_Table(DatabaseDefinition databaseDefinition) {
    super(databaseDefinition);
  }

  @Override
  public final Class<ProfileData> getModelClass() {
    return ProfileData.class;
  }

  @Override
  public final String getTableName() {
    return "`ProfileData`";
  }

  @Override
  public final ProfileData newInstance() {
    return new ProfileData();
  }

  @Override
  public final Property getProperty(String columnName) {
    columnName = QueryBuilder.quoteIfNeeded(columnName);
    switch ((columnName)) {
      case "`id`":  {
        return id;
      }
      case "`name`":  {
        return name;
      }
      case "`identifier`":  {
        return identifier;
      }
      case "`attibutes`":  {
        return attibutes;
      }
      case "`parentProfile`":  {
        return parentProfile;
      }
      default: {
        throw new IllegalArgumentException("Invalid column name passed. Ensure you are calling the correct table's column");
      }
    }
  }

  @Override
  public final void updateAutoIncrement(ProfileData model, Number id) {
    model.id = id.longValue();
  }

  @Override
  public final Number getAutoIncrementingId(ProfileData model) {
    return model.id;
  }

  @Override
  public final String getAutoIncrementingColumnName() {
    return "id";
  }

  @Override
  public final ModelSaver<ProfileData> createSingleModelSaver() {
    return new AutoIncrementModelSaver<>();
  }

  @Override
  public final IProperty[] getAllColumnProperties() {
    return ALL_COLUMN_PROPERTIES;
  }

  @Override
  public final void bindToInsertValues(ContentValues values, ProfileData model) {
    values.put("`name`", model.getName());
    values.put("`identifier`", model.getIdentifier());
    values.put("`attibutes`", model.getAttibutes());
    values.put("`parentProfile`", model.getParentProfile());
  }

  @Override
  public final void bindToContentValues(ContentValues values, ProfileData model) {
    values.put("`id`", model.id);
    bindToInsertValues(values, model);
  }

  @Override
  public final void bindToInsertStatement(DatabaseStatement statement, ProfileData model,
      int start) {
    statement.bindStringOrNull(1 + start, model.getName());
    statement.bindStringOrNull(2 + start, model.getIdentifier());
    statement.bindStringOrNull(3 + start, model.getAttibutes());
    statement.bindStringOrNull(4 + start, model.getParentProfile());
  }

  @Override
  public final void bindToStatement(DatabaseStatement statement, ProfileData model) {
    int start = 0;
    statement.bindLong(1 + start, model.id);
    bindToInsertStatement(statement, model, 1);
  }

  @Override
  public final void bindToUpdateStatement(DatabaseStatement statement, ProfileData model) {
    statement.bindLong(1, model.id);
    statement.bindStringOrNull(2, model.getName());
    statement.bindStringOrNull(3, model.getIdentifier());
    statement.bindStringOrNull(4, model.getAttibutes());
    statement.bindStringOrNull(5, model.getParentProfile());
    statement.bindLong(6, model.id);
  }

  @Override
  public final void bindToDeleteStatement(DatabaseStatement statement, ProfileData model) {
    statement.bindLong(1, model.id);
  }

  @Override
  public final String getInsertStatementQuery() {
    return "INSERT INTO `ProfileData`(`name`,`identifier`,`attibutes`,`parentProfile`) VALUES (?,?,?,?)";
  }

  @Override
  public final String getCompiledStatementQuery() {
    return "INSERT INTO `ProfileData`(`id`,`name`,`identifier`,`attibutes`,`parentProfile`) VALUES (?,?,?,?,?)";
  }

  @Override
  public final String getUpdateStatementQuery() {
    return "UPDATE `ProfileData` SET `id`=?,`name`=?,`identifier`=?,`attibutes`=?,`parentProfile`=? WHERE `id`=?";
  }

  @Override
  public final String getDeleteStatementQuery() {
    return "DELETE FROM `ProfileData` WHERE `id`=?";
  }

  @Override
  public final String getCreationQuery() {
    return "CREATE TABLE IF NOT EXISTS `ProfileData`(`id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` TEXT, `identifier` TEXT, `attibutes` TEXT, `parentProfile` TEXT)";
  }

  @Override
  public final void loadFromCursor(FlowCursor cursor, ProfileData model) {
    model.id = cursor.getLongOrDefault("id");
    model.setName(cursor.getStringOrDefault("name"));
    model.setIdentifier(cursor.getStringOrDefault("identifier"));
    model.setAttibutes(cursor.getStringOrDefault("attibutes"));
    model.setParentProfile(cursor.getStringOrDefault("parentProfile"));
  }

  @Override
  public final boolean exists(ProfileData model, DatabaseWrapper wrapper) {
    return model.id > 0
    && SQLite.selectCountOf()
    .from(ProfileData.class)
    .where(getPrimaryConditionClause(model))
    .hasData(wrapper);
  }

  @Override
  public final OperatorGroup getPrimaryConditionClause(ProfileData model) {
    OperatorGroup clause = OperatorGroup.clause();
    clause.and(id.eq(model.id));
    return clause;
  }
}
