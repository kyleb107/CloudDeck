package com.kylebarnes.clouddeck.storage;

import com.kylebarnes.clouddeck.model.AircraftProfile;

import java.util.List;

public interface AircraftProfileRepository {
    List<AircraftProfile> loadProfiles();

    boolean saveProfile(AircraftProfile profile);

    boolean removeProfile(String profileName);
}
