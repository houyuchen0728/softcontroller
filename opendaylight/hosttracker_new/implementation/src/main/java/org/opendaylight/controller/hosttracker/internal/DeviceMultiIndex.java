/*
 * Copyright (c) 2012 Big Switch Networks, Inc.
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 *    Originally created by David Erickson, Stanford University
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the
 *    License. You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing,
 *    software distributed under the License is distributed on an "AS
 *    IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *    express or implied. See the License for the specific language
 *    governing permissions and limitations under the License.
 */

package org.opendaylight.controller.hosttracker.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opendaylight.controller.hosttracker.Entity;
import org.opendaylight.controller.hosttracker.IDeviceService.DeviceField;
import org.opendaylight.controller.sal.utils.IterableIterator;

/**
 * An index that maps key fields of an entity to device keys, with multiple
 * device keys allowed per entity
 */
public class DeviceMultiIndex extends DeviceIndex {
    /**
     * The index
     */
    private ConcurrentHashMap<IndexedEntity, Collection<Long>> index;

    /**
     * @param keyFields
     */
    public DeviceMultiIndex(EnumSet<DeviceField> keyFields) {
        super(keyFields);
        index = new ConcurrentHashMap<IndexedEntity, Collection<Long>>();
    }

    // ***********
    // DeviceIndex
    // ***********

    @Override
    public Iterator<Long> queryByEntity(Entity entity) {
        IndexedEntity ie = new IndexedEntity(keyFields, entity);
        Collection<Long> devices = index.get(ie);
        if (devices != null)
            return devices.iterator();

        return Collections.<Long> emptySet().iterator();
    }

    @Override
    public Iterator<Long> getAll() {
        Iterator<Collection<Long>> iter = index.values().iterator();
        return new IterableIterator<Long>(iter);
    }

    @Override
    public boolean updateIndex(Device device, Long deviceKey) {
        for (Entity e : device.entities) {
            updateIndex(e, deviceKey);
        }
        return true;
    }

    @Override
    public void updateIndex(Entity entity, Long deviceKey) {
        Collection<Long> devices = null;

        IndexedEntity ie = new IndexedEntity(keyFields, entity);
        if (!ie.hasNonNullKeys())
            return;

        devices = index.get(ie);
        if (devices == null) {
            Map<Long, Boolean> chm = new ConcurrentHashMap<Long, Boolean>();
            devices = Collections.newSetFromMap(chm);
            Collection<Long> r = index.putIfAbsent(ie, devices);
            if (r != null)
                devices = r;
        }

        devices.add(deviceKey);
    }

    @Override
    public void removeEntity(Entity entity) {
        IndexedEntity ie = new IndexedEntity(keyFields, entity);
        index.remove(ie);
    }

    @Override
    public void removeEntity(Entity entity, Long deviceKey) {
        IndexedEntity ie = new IndexedEntity(keyFields, entity);
        Collection<Long> devices = index.get(ie);
        if (devices != null)
            devices.remove(deviceKey);
    }
}
