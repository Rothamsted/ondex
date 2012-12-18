package net.sourceforge.ondex.restful.resources.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Set;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import net.sourceforge.ondex.core.Attribute;
import net.sourceforge.ondex.core.AttributeName;
import net.sourceforge.ondex.core.ONDEXConcept;
import net.sourceforge.ondex.core.ONDEXEntity;
import net.sourceforge.ondex.core.ONDEXRelation;
import net.sourceforge.ondex.export.oxl.Export;
import net.sourceforge.ondex.restful.util.WstxOutputFactoryProvider;

import org.codehaus.stax2.XMLStreamWriter2;

import com.ctc.wstx.io.CharsetNames;
import com.sun.jersey.spi.resource.Singleton;

/**
 * Passes XML encoding of a list of ConceptAttribute to output stream or simply
 * HTML.
 * 
 * @author taubertj
 */
@Provider
@Singleton
@Produces({ MediaType.APPLICATION_XML, MediaType.TEXT_HTML })
public class AttributesMessageBodyWriter extends Export implements
		MessageBodyWriter<Set<Attribute>> {

	public AttributesMessageBodyWriter() throws JAXBException {
		super();
	}

	/**
	 * Used to construct the URI linking services.
	 */
	@Context
	private UriInfo ui;

	@Override
	public long getSize(Set<Attribute> iterator, Class<?> clazz, Type type,
			Annotation[] anno, MediaType mediatype) {
		return -1;
	}

	@Override
	public boolean isWriteable(Class<?> clazz, Type type, Annotation[] anno,
			MediaType mediatype) {
		// accept all subclasses of Set
		boolean accept = Set.class.isAssignableFrom(clazz);
		if (type instanceof ParameterizedType) {
			Object first = ((ParameterizedType) type).getActualTypeArguments()[0];
			if (first instanceof Class<?>)
				accept = accept
						&& Attribute.class.isAssignableFrom((Class<?>) first);
			else
				accept = false;
		} else
			accept = false;
		return accept;
	}

	@Override
	public void writeTo(Set<Attribute> set, Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, Object> httpHeaders,
			OutputStream entityStream) throws IOException,
			WebApplicationException {

		// make sure there is a set
		if (set == null)
			throw new WebApplicationException(Response.Status.NOT_FOUND);

		// return XML encoding
		if (mediaType.toString().equals(MediaType.APPLICATION_XML)) {
			try {
				// new XML writer for output stream
				XMLStreamWriter2 xmlWriteStream = (XMLStreamWriter2) WstxOutputFactoryProvider.xmlw
						.createXMLStreamWriter(entityStream,
								CharsetNames.CS_UTF8);

				// enable legacy mode for fully expanded meta data
				setLegacyMode(true);

				// retrieve class for this set
				Iterator<Attribute> it = set.iterator();
				Class<? extends ONDEXEntity> clazz = it.next().getOwnerClass();

				// export list of attributes
				if (clazz.isAssignableFrom(ONDEXConcept.class))
					buildConceptAttributes(xmlWriteStream, set);
				else if (clazz.isAssignableFrom(ONDEXRelation.class))
					buildRelationAttributes(xmlWriteStream, set);
				else
					throw new WebApplicationException(Response.Status.CONFLICT);

				// flush out all data
				xmlWriteStream.flush();
			} catch (XMLStreamException e) {
				throw new WebApplicationException(e,
						Response.Status.INTERNAL_SERVER_ERROR);
			} catch (JAXBException e) {
				throw new WebApplicationException(e,
						Response.Status.INTERNAL_SERVER_ERROR);
			}
		}

		// return HTML encoding
		else if (mediaType.toString().equals(MediaType.TEXT_HTML)) {
			String path = ui.getAbsolutePath().getPath();
			if (path.endsWith("/"))
				path = path.substring(0, path.length() - 1);
			String meta = path.substring(0,
					path.indexOf("/", path.indexOf("graphs/") + 7));
			meta = meta + "/metadata";

			// simply write HTML code
			OutputStreamWriter writer = new OutputStreamWriter(entityStream);
			writer.write("<h2>Attributes</h2>\n");
			writer.write("<table><tr><th>attribute name</th><th>value</th></tr>\n");
			for (Attribute attribute : set) {
				// link AtrributeName
				AttributeName an = attribute.getOfType();
				writer.write("<tr><td><a href=\"");
				writer.write(meta + "/attributenames/" + an.getId());
				writer.write("\">");
				writer.write(an.getId());
				writer.write("</a></td>\n");

				writer.write("<td><a href=\"");
				writer.write(path + "/" + attribute.getOfType().getId());
				writer.write("\">");
				writer.write(attribute.getValue().toString());
				writer.write("</a></tr>\n");
			}
			writer.write("</table>\n");
			writer.write("<a href=\"");
			writer.write(path.substring(0, path.lastIndexOf("/")));
			writer.write("\">up</a>");
			writer.flush();
		}
	}
}