package org.matonto.jaas.api.engines;

/*-
 * #%L
 * org.matonto.jaas.api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 iNovex Information Systems, Inc.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import org.matonto.jaas.api.ontologies.usermanagement.Group;
import org.matonto.jaas.api.ontologies.usermanagement.Role;
import org.matonto.jaas.api.ontologies.usermanagement.User;

import java.util.Optional;
import java.util.Set;

public interface EngineManager {
    /**
     * Returns a boolean indicating whether the EngineManager contains an Engine
     * with the passed name.
     *
     * @param engine the name of the Engine to test for
     * @return true if the engine is in the manager; false otherwise
     */
    boolean containsEngine(String engine);

    /**
     * Returns the Set of Users accessible by the Engine with the passed name.
     *
     * @param engine the name of the Engine to collect Users from
     * @return the Set of Users accessible by the specified Engine
     */
    Set<User> getUsers(String engine);

    /**
     * Creates a new User object using the Engine with the passed name.
     *
     * @param engine the name of the Engine to use when creating the User
     * @param userConfig a configuration for the new User
     * @return a User with properties set by the passed configuration object as determined
     *      by the specified Engine
     */
    User createUser(String engine, UserConfig userConfig);

    /**
     * Stores the passed User using the Engine with the passed name. Returns a boolean
     * indicating the success of the addition.
     *
     * @param engine the name of the Engine to store the User with
     * @param user the User to store
     * @return true if the addition of the User was successful; false otherwise
     */
    boolean storeUser(String engine, User user);

    /**
     * Attempts to retrieve the User with the passed username using the Engine with the
     * passed username. Returns an Optional with the User if it was found or empty if it
     * could not be found.
     *
     * @param engine the name of the Engine to retrieve the User with
     * @param username the username of the User to retrieve
     * @return an Optional that contains the User if present; empty otherwise
     */
    Optional<User> retrieveUser(String engine, String username);

    /**
     * Removes the User with the passed username using the Engine with the passed name.
     * Returns a boolean indicating the success of the deletion.
     *
     * @param engine the name of the Engine to delete the User with
     * @param username the username of the User to delete
     * @return true if the deletion of the User was successful; false otherwise
     */
    boolean deleteUser(String engine, String username);

    /**
     * Replaces the User with the same identifier as the passed User with the new User object
     * using the Engine with the passed name. Returns a boolean indicating the success of the
     * update.
     *
     * @param engine the name of the Engine to update the User with
     * @param newUser the new User object to replace the existing one
     * @return true if the update of the User was successful; false otherwise
     */
    boolean updateUser(String engine, User newUser);

    /**
     * Returns a boolean indicating whether a User with the passed username exists using the
     * Engine with the passed name.
     *
     * @param engine the name of the Engine to test for the existence of the User with
     * @param username the username to look for
     * @return true if a User exists with the passed username; false otherwise
     */
    boolean userExists(String engine, String username);

    /**
     * Returns a boolean indicating whether a User with the passed username exists using any
     * of the Engines managed by the EngineManager.
     *
     * @param username the username to look for
     * @return true if a User exists with the passed username; false otherwise
     */
    boolean userExists(String username);

    /**
     * Returns the Set of Groups accessible by the Engine with the passed name.
     *
     * @param engine the name of the Engine to collect Group from
     * @return the Set of Groups accessible by the specified Engine
     */
    Set<Group> getGroups(String engine);

    /**
     * Creates a new Group object using the Engine with the passed name.
     *
     * @param engine the name of the Engine to use when creating the Group
     * @param groupConfig a configuration for the new Group
     * @return a User with properties set by the passed configuration object as determined
     *      by the specified Engine
     */
    Group createGroup(String engine, GroupConfig groupConfig);

    /**
     * Stores the passed Group using the Engine with the passed name. Returns a boolean
     * indicating the success of the addition.
     *
     * @param engine the name of the Engine to store the Group with
     * @param group the Group to store
     * @return true if the addition of the Group was successful; false otherwise
     */
    boolean storeGroup(String engine, Group group);

    /**
     * Attempts to retrieve the Group with the passed username using the Engine with the
     * passed username. Returns an Optional with the Group if it was found or empty if it
     * could not be found.
     *
     * @param engine the name of the Engine to retrieve the Group with
     * @param groupName the name of the Group to retrieve
     * @return an Optional that contains the Group if present; empty otherwise
     */
    Optional<Group> retrieveGroup(String engine, String groupName);

    /**
     * Removes the Group with the passed name using the Engine with the passed name.
     * Returns a boolean indicating the success of the deletion.
     *
     * @param engine the name of the Engine to delete the Group with
     * @param groupName the name of the Group to delete
     * @return true if the deletion of the Group was successful; false otherwise
     */
    boolean deleteGroup(String engine, String groupName);

    /**
     * Replaces the Group with the same identifier as the passed Group with the new Group object
     * using the Engine with the passed name. Returns a boolean indicating the success of the
     * update.
     *
     * @param engine the name of the Engine to update the Group with
     * @param newGroup the new Group object to replace the existing one
     * @return true if the update of the Group was successful; false otherwise
     */
    boolean updateGroup(String engine, Group newGroup);

    /**
     * Returns a boolean indicating whether a Group with the passed name exists using the
     * Engine with the passed name.
     *
     * @param engine the name of the Engine to test for the existence of the Group with
     * @param groupName the group name to look for
     * @return true if a Group exists with the passed name; false otherwise
     */
    boolean groupExists(String engine, String groupName);

    /**
     * Returns a boolean indicating whether a Group with the passed name exists using any
     * of the Engines managed by the EngineManager.
     *
     * @param groupName the group name to look for
     * @return true if a Group exists with the passed name; false otherwise
     */
    boolean groupExists(String groupName);

    /**
     * Retrieves the set of all roles that the User with the passed username embodies
     * using the Engine with the passed name. This set should contain all roles from
     * all the User's groups as well.
     *
     * @param engine the name of the Engine to collect user roles from
     * @param username the username of the User to collect the roles for
     * @return the Set of Roles that the User embodies
     */
    Set<Role> getUserRoles(String engine, String username);

    /**
     * Retrieves the full set of all roles that the User with the passed username embodies
     * using all Engines managed by the EngineManager. This set should contain all roles from
     * all the User's groups as well.
     *
     * @param username the username of the User to collect the roles for
     * @return the Set of Roles that the User embodies
     */
    Set<Role> getUserRoles(String username);

    /**
     * Returns a boolean indicating whether the passed password matches the saved password for
     * the User with the passed username using the Engine with the passed name.
     *
     * @param engine the name of the Engine to use when testing passwords
     * @param username the username for the User to test the password of
     * @param password the password to test
     * @return true if the passwords match; false otherwise
     */
    boolean checkPassword(String engine, String username, String password);
}
