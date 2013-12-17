package net.sourceforge.ondex.restful.resources.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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

import net.sourceforge.ondex.core.EvidenceType;
import net.sourceforge.ondex.export.oxl.Export;
import net.sourceforge.ondex.restful.util.WstxOutputFactoryProvider;

import org.codehaus.stax2.XMLStreamWriter2;

import com.ctc.wstx.io.CharsetNames;
import com.sun.jersey.spi.resource.Singleton;

/**
 * Passes XML encoding of a list of EvidenceTypes to output stream or simply
 * HTML.
 * 
 * @author taubertj
 * 
 */
@Provider
@Singleton
@Produces({ MediaType.APPLICATION_XML, MediaType.TEXT_HTML })
public class EvidenceTypesMessageBodyWriter extends Export implements
		MessageBodyWriter<Set<EvidenceType>> {

	public EvidenceTypesMessageBodyWriter() throws JAXBException {
		super();
	}

	/**
	 * Used to construct the URI linking services.
	 */
	@Context
	private UriInfo ui;

	@Override
	public long getSize(Set<EvidenceType> iterator, Class<?> clazz, Type type,
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
						&& EvidenceType.class
								.isAssignableFrom((Class<?>) first);
			else
				accept = false;
		} else
			accept = false;
		return accept;
	}

	@Override
	public void writeTo(Set<EvidenceType> iterator, Class<?> clazz, Type type,
			Annotation[] anno, MediaType mediatype,
			MultivaluedMap<String, Object> map, OutputStream outstream)
			throws IOException, WebApplicationException {

		// make sure there is a iterator
		if (iterator == null)
			throw new WebApplicationException(Response.Status.NOT_FOUND);

		// return XML encoding
		if (mediatype.toString().equals(MediaType.APPLICATION_XML)) {
			try {
				// new XML writer for output stream
				XMLStreamWriter2 xmlWriteStream = (XMLStreamWriter2) WstxOutputFactoryProvider.xmlw
						.createXMLStreamWriter(outstream, CharsetNames.CS_UTF8);

				// enable legacy mode for fully expanded meta data
				setLegacyMode(true);

				// export list of EvidenceTypes
				buildEvidences(xmlWriteStream, iterator, RefOrVal.VAL);

				// flush out all data
				xmlWriteStream.flush();
			} catch (XMLStreamException e) {
				throw new WebApplicationException(e,
						Response.Status.INTERNAL_SERVER_ERROR);
			}
		}

		// return HTML encoding
		else if (mediatype.toString().equals(MediaType.TEXT_HTML)) {
			String path = ui.getAbsolutePath().getPath();
			if (path.endsWith("/"))
				path = path.substring(0, path.length() - 1);

			// simply write HTML code
			OutputStreamWriter writer = new OutputStreamWriter(outstream);
			writer.write("<h2>EvidenceTypes</h2>\n");
			writer.write("<table><tr><th>id</th><th>fullname</th></tr>\n");
			for (EvidenceType evidencetype : iterator) {
				writer.write("<tr><td><a href=\"");
				writer.write(path + "/" + evidencetype.getId());
				writer.write("\">");
				writer.write(evidencetype.getId());
				writer.write("</a>");
				writer.write("</td><td>");
				writer.write(evidencetype.getFullname());
				writer.write("</td></tr>\n");
			}
			writer.write("</table>\n");
			writer.write("<a href=\"");
			writer.write(path.substring(0, path.lastIndexOf("/")));
			writer.write("\">up</a>");
			writer.flush();
		}
	}
}
