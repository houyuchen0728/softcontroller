/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.concepts.lang;
import java.util.Collection;
/**
 *
 * @author Tony Tkacik
 *
 * @param <I>
 * @param <P>
 */
public interface AggregateTransformer<I,P> extends Transformer<I,P> {

    Collection<P> transformAll(Collection<? extends I> inputs);
}
