/*
 * #%L
 * Tutorials for SCIFIO API
 * %%
 * Copyright (C) 2011 - 2013 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * To the extent possible under law, the SCIFIO developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 * 
 * See the CC0 1.0 Universal license for details:
 * http://creativecommons.org/publicdomain/zero/1.0/
 * #L%
 */

package io.scif.tutorials;

import io.scif.Format;
import io.scif.FormatException;
import io.scif.SCIFIO;
import io.scif.formats.FakeFormat;
import io.scif.services.FormatService;

import org.scijava.Context;
import org.scijava.plugin.PluginService;

/**
 * A guide to using the scijava context for discovery in SCIFIO.
 * 
 * @author Mark Hiner
 */
public class T3aDiscoveryTutorial {

	public static void main(final String... args) throws FormatException {
		// The org.scijava.Context provides a framework for automatic discovery
		// which SCIFIO uses extensively. In the briefest of summaries, each
		// context instance is a container for two general types of discoverables:
		// - org.scijava.plugin.Plugin. Typically plugins are classified by "type",
		// a common interface, with different instances of that type required
		// depending on the situation. io.scif.Format and io.scif.Translator
		// are types of plugins you may want to discover in SCIFIO.
		// - org.scijava.service.Service. Services act more like static utility
		// classes within their parent context. Each is instantiated once
		// by the context and called upon repeatedly to provide its functionality.
		// In SCIFIO, the TranslatorService and FormatService are provided to
		// perform convenient operations regarding managing instances of the
		// Format and Translator plugins.

		// To explore these concepts more practically, we will start by creating a
		// context:
		final Context context = new Context();

		// Now, let's get a SCIFIO to work with:
		final SCIFIO scifio = new SCIFIO(context);

		// The SCIFIO service was created as a convenience entry point to access the
		// other commonly required services. For example, if we want to work with
		// Formats, we can get a FormatService implementation:
		final FormatService fService = scifio.format();

		// Note that this service could also have been retrieved from the context
		// itself, and
		// because it is a service you would get the same instance back:

		final FormatService fService2 = scifio.format();
		System.out.println("Format services are equal: " + (fService == fService2));

		// Note that FormatService is an interface. If you look at
		// io.scif.DefaultFormatService
		// you'll see that it's annotated as a Plugin with type=FormatService.class.
		// This
		// allows it to be discovered by the context as a FormatService instance.
		// If more than one class on the classpath was similarly annotated, the
		// context
		// would have returned the instance with the highest priority field in its
		// Plugin annotation.

		// Now let's play with a sample image path
		final String sampleImage =
			"8bit-signed&pixelType=int8&sizeZ=3&sizeC=5&sizeT=7&sizeY=50.fake";

		// We can use our FormatService to find an appropriate Format plugin:
		final Format format = fService.getFormat(sampleImage);

		// Formats are special in that they are maintained as singletons within a
		// FormatService:

		final Format format2 = fService2.getFormat(sampleImage);
		System.out.println("Formats from FormatService are equal: " +
			(format == format2));

		// We'll look at creating plugins now. To do so we'll want to use the
		// PluginService:
		final PluginService pluginService = context.getService(PluginService.class);

		// However, that is special behavior of the FormatService in particular. If
		// we were manually
		// querying a Format plugin from the context, it would return a new
		// instance:
		final Format format3 =
			pluginService.createInstancesOfType(FakeFormat.class).get(0);

		System.out.println("Formats from the context are equal: " +
			(format == format3));

		// A couple of things to note here:
		// - PluginService is a very handy Service to use when you need to
		// instantiate a plugin.
		// - Annotating a plugin using the narrowest type possible allows for
		// granular discovery.
		// FakeFormat, the concrete class, can be discovered directly. We could also
		// have done
		// something like:

		Format format4 = null;

		for (final Format f : pluginService.createInstancesOfType(Format.class)) {
			if (f.getClass().equals(FakeFormat.class)) format4 = f;
		}

		System.out.println("FakeFormat found: " + (format4 != null));

		// Understanding the Plugin annotation is critical to developing new SCIFIO
		// components.
		// Some general tips to keep in mind:
		// - Always set Type to the most specific class possible for maximum
		// granularity
		// - use the "priority" field to control the order plugins are returned
		// - use the attrs() field to allow for string-matching queries.

		// For a discussion of how to define your own services, take a look at
		// T3bCustomFormats

		// For examples of using and querying attributes, look at
		// io.scifDefaultTranslatorService,
		// io.scif.PluginAttributeService and the Translator implementations.
	}
}