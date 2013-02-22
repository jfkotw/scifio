/*
 * #%L
 * OME SCIFIO package for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2005 - 2013 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */
package ome.scifio.filters;

import java.util.Set;

import org.scijava.Contextual;

/**
 * An entry point for toggling and configuring {@link ome.scifio.filter.Filter}s.
 * <p>
 * When instantiated, all appropriately typed {@code Filter} classes
 * should be discovered by this class. As {@link #enable(Class)} and {@link #disable(Class)}
 * are called on the appropriate {@code Filter} classes, a stack of filters is
 * built.
 * </p>
 * <p>
 * NB: this interface extends {@link ome.scifio.Filter} and concrete {@code MasterFilter} 
 * implementations should similarly extend the interface they wrap. However, instead of
 * {@link ome.scifio.filters.Filter#getParent()} returning the wrapped object,
 * it should return the head of this {@code MasterFilter}'s filter stack.
 * </p>
 * <p>
 * NB: all wrappers should be maintained as singletons within the scope of a
 * {@code MasterFilter} instance, so that {@link #enable(Class)} returns
 * the same filter instance each time it is called.
 * </p>
 * <p>
 * NB: {@code MasterFilters} are intended to be used the same way as {@code Filters}:
 * that is, as the wrapped component would be. However, the {@code Filter} API 
 * does have a slightly different meaning in the context of a {@code MasterFilter}.
 * </p>
 * 
 * @author Mark Hiner
 * 
 * @see ome.scifio.filters.Filter
 */
public interface MasterFilter<T extends Contextual> extends Filter {

  /**
   * Inserts an instance of the indicated filter class into the
   * filter stack. Returns the filter instance associated with
   * this MasterFilter, which can be used for wrapper-specific 
   * configuration.
   * 
   * @param wrapperClass
   * @return
   */
  <F extends Filter> F enable(Class<F> filterClass);
  
  /**
   * Removes the specified filter from the filter stack,
   * if present. Clears any state in the cached instance of the specified
   * filter.
   * 
   * @param wrapperClass
   * @return true if the desired wrapper was disabled
   */
  boolean disable(Class<? extends Filter> filterClass);
  
  /**
   * Returns a list of all filter classes this MasterFilter can enable/disable.
   * 
   * @return
   */
  Set<Class<? extends Filter>> getFilterClasses();
  
  // -- Filter API --
  
  /**
   * Sets the wrapped object. Effectively the tail of the filter stack.
   */
  void setParent(Object parent);
  
  /**
   * Returns the top of the filter stack.
   */
  Object getParent();
  
  /**
   * Disables all enabled filters maintained by this MasterFilter.
   */
  void reset();
}