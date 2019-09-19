package com.raizlabs.android.dbflow.config;

import dev.ukanth.ufirewall.profiles.ProfileData_Table;
import dev.ukanth.ufirewall.profiles.ProfilesDatabase;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import javax.annotation.Generated;

/**
 * This is generated code. Please do not modify */
@Generated("com.raizlabs.android.dbflow.processor.DBFlowProcessor")
public final class ProfilesDatabaseProfilesDatabase_Database extends DatabaseDefinition {
  public ProfilesDatabaseProfilesDatabase_Database(DatabaseHolder holder) {
    addModelAdapter(new ProfileData_Table(this), holder);
  }

  @Override
  public final Class<?> getAssociatedDatabaseClassFile() {
    return ProfilesDatabase.class;
  }

  @Override
  public final boolean isForeignKeysSupported() {
    return false;
  }

  @Override
  public final boolean backupEnabled() {
    return false;
  }

  @Override
  public final boolean areConsistencyChecksEnabled() {
    return false;
  }

  @Override
  public final int getDatabaseVersion() {
    return 1;
  }

  @Override
  public final String getDatabaseName() {
    return "profiles";
  }
}
