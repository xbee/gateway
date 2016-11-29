/**
 * Copyright 2007-2016, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kaazing.gateway.service.messaging.collections;

import com.hazelcast.core.EntryListener;
import com.hazelcast.core.IList;
import com.hazelcast.core.ILock;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.ITopic;

import org.kaazing.gateway.util.AtomicCounter;

public interface CollectionsFactory {

    <K, V> IMap<K, V> getMap(String name);

    <E> IQueue<E> getQueue(String name);

    <E> IList<E> getList(String name);

    <E> ITopic<E> getTopic(String name);

    ILock getLock(String name);

    <K, V> void addEntryListener(EntryListener<K, V> listener, String name);

    AtomicCounter getAtomicCounter(String name);
}
