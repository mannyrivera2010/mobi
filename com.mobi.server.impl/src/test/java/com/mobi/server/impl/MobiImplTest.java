package com.mobi.server.impl;

/*-
 * #%L
 * com.mobi.platform.config.impl
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 - 2017 iNovex Information Systems, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mobi.server.api.ServerUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MobiImplTest {

    @Mock
    private ConfigurationAdmin configurationAdmin;

    @Mock
    private Configuration configuration;

    @Mock
    private ServerUtils utils;

    @Captor
    private ArgumentCaptor<Dictionary<String, Object>> captor;

    @Test
    public void testGeneratedServerIdIsSaved() throws Exception {
        when(utils.getMacId()).thenReturn("serverId".getBytes());
        when(configurationAdmin.getConfiguration(anyString())).thenReturn(configuration);
        MobiImpl impl = new MobiImpl();
        impl.setConfigurationAdmin(configurationAdmin);
        impl.setServerUtils(utils);
        impl.activate(new HashMap<>());
        verify(configuration).update(captor.capture());
        verify(utils).getMacId();
        assertEquals(impl.getServerIdentifier().toString(), captor.getValue().get("serverId"));
    }

    @Test
    public void testAlreadyHasServerId() throws Exception {
        MobiImpl impl = new MobiImpl();
        String val = UUID.randomUUID().toString();
        impl.activate(Collections.singletonMap("serverId", val));
        assertEquals(val, impl.getServerIdentifier().toString());
    }

    @Test
    public void testHostNameProvided() throws Exception {
        MobiImpl impl = new MobiImpl();
        String val = UUID.randomUUID().toString();
        String hostName = "https://www.google.com";
        Map<String, Object> config = new HashMap<>();
        config.put("serverId", val);
        config.put("hostName", hostName);

        impl.activate(config);

        assertEquals(hostName, impl.getHostName());
    }

    @Test
    public void testHostNameIPProvided() throws Exception {
        MobiImpl impl = new MobiImpl();
        String val = UUID.randomUUID().toString();
        String hostName = "http://8.8.8.8";
        Map<String, Object> config = new HashMap<>();
        config.put("serverId", val);
        config.put("hostName", hostName);

        impl.activate(config);
        assertEquals(hostName, impl.getHostName());
    }

    @Test
    public void testHostNameIPNoProtocolProvided() throws Exception {
        MobiImpl impl = new MobiImpl();
        String val = UUID.randomUUID().toString();
        String hostName = "8.8.8.8";
        Map<String, Object> config = new HashMap<>();
        config.put("serverId", val);
        config.put("hostName", hostName);

        impl.activate(config);
        assertEquals("", impl.getHostName());
    }

    @Test
    public void testHostNameLocalHostProvided() throws Exception {
        MobiImpl impl = new MobiImpl();
        String val = UUID.randomUUID().toString();
        String hostName = "https://localhost:8443";
        Map<String, Object> config = new HashMap<>();
        config.put("serverId", val);
        config.put("hostName", hostName);

        impl.activate(config);
        assertEquals(hostName, impl.getHostName());
    }

    @Test
    public void testHostNameLocalHostIPProvided() throws Exception {
        MobiImpl impl = new MobiImpl();
        String val = UUID.randomUUID().toString();
        String hostName = "http://127.0.0.1";
        Map<String, Object> config = new HashMap<>();
        config.put("serverId", val);
        config.put("hostName", hostName);

        impl.activate(config);
        assertEquals(hostName, impl.getHostName());
    }

    @Test
    public void testHostNameProvidedInvalid() throws Exception {
        MobiImpl impl = new MobiImpl();
        String val = UUID.randomUUID().toString();
        String hostName = "someBadHostName";
        Map<String, Object> config = new HashMap<>();
        config.put("serverId", val);
        config.put("hostName", hostName);

        impl.activate(config);
        assertEquals("", impl.getHostName());
    }

    @Test
    public void testHostNameNotProvided() throws Exception {
        MobiImpl impl = new MobiImpl();
        String val = UUID.randomUUID().toString();
        impl.activate(Collections.singletonMap("serverId", val));

        assertEquals("", impl.getHostName());
    }

}
