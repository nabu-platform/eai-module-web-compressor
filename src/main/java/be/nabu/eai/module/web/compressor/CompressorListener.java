package be.nabu.eai.module.web.compressor;

import java.io.IOException;
import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.LinkableHTTPResponse;
import be.nabu.libs.http.core.DefaultHTTPResponse;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.ModifiablePart;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.MimeUtils;
import be.nabu.utils.mime.impl.PlainMimeContentPart;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SourceFile;

// https://developers.google.com/closure/compiler/
public class CompressorListener implements EventHandler<HTTPResponse, HTTPResponse> {

	private Logger logger = LoggerFactory.getLogger(getClass());
	private CompressorArtifact artifact;
	
	public CompressorListener(CompressorArtifact artifact) {
		this.artifact = artifact;
	}
	
	@Override
	public HTTPResponse handle(HTTPResponse event) {
		if (!EAIResourceRepository.isDevelopment()) {
			String contentType = MimeUtils.getContentType(event.getContent().getHeaders());
			if ("application/javascript".equals(contentType) || "text/css".equals(contentType)) {
				try {
					byte[] originalContent = null;
					byte [] compressedContent = null;
					
					String hash = null;
					// only makes sense to calculate an etag if we can cache the result
					if (artifact.getConfig().getCacheProvider() != null) {
						// check for etag
						Header etag = MimeUtils.getHeader("Etag", event.getContent().getHeaders());
						if (etag != null) {
							hash = etag.getValue();
						}
						// we need to calculate the hash
						if (hash == null) {
							originalContent = readContent((ContentPart) event.getContent());
							hash = GlueListener.hash(originalContent, "MD-5");
						}
						compressedContent = (byte[]) artifact.getConfig().getCacheProvider().get(artifact.getId()).get(hash);
					}
					
					if (compressedContent == null) {
						Charset charset = artifact.getConfig().getCharset();
						if (charset == null) {
							charset = Charset.defaultCharset();
						}
						
						if (originalContent == null) {
							originalContent = readContent((ContentPart) event.getContent());
						}
	
						if ("application/javascript".equals(contentType)) {
							CompilerOptions options = new CompilerOptions();
							CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
	//						CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
							options.setLanguageIn(LanguageMode.ECMASCRIPT5);
								
							SourceFile file = SourceFile.fromCode("file.js", new String(originalContent, charset));
							SourceFile external = SourceFile.fromCode("file2.js", "");
							
							com.google.javascript.jscomp.Compiler compiler = new com.google.javascript.jscomp.Compiler();
							compiler.compile(external, file, options);
	
							String source = compiler.toSource();
							compressedContent = source.getBytes(charset);
						}
						else if ("text/css".equals(contentType)) {
							String css = new String(originalContent, charset);
							// strip comments
							css = css.replaceAll("(?s)/\\*.*?\\*/", "");
							// strip all whitespace
							css = css.replaceAll("(\\{|:|;|\\}|,)[\\s]+", "$1");
							compressedContent = css.getBytes(charset);
						}
						
						// if we just built the content, store it (if possible)
						if (compressedContent != null && hash != null) {
							artifact.getConfig().getCacheProvider().get(artifact.getId()).put(hash, compressedContent);
						}
					}
					
					if (compressedContent != null) {
						ModifiablePart part = new PlainMimeContentPart(null, IOUtils.wrap(compressedContent, true), event.getContent().getHeaders());
						
						Header header = MimeUtils.getHeader("Content-Length", part.getHeaders());
						if (header != null) {
							part.removeHeader("Content-Length");
							part.setHeader(new MimeHeader("Content-Length", "" + compressedContent.length));
						}
						
						return new DefaultHTTPResponse(
							event instanceof LinkableHTTPResponse ? ((LinkableHTTPResponse) event).getRequest() : null,
							event.getCode(), 
							event.getMessage(), 
							part
						);
					}
				}
				catch (IOException e) {
					logger.error("Could not compress javascript result", e);
				}
			}
		}
		return null;
	}

	private byte [] readContent(ContentPart part) throws IOException {
		ReadableContainer<ByteBuffer> readable = part.getReadable();
		if (readable != null) {
			try {
				return IOUtils.toBytes(readable);
			}
			finally {
				readable.close();
			}
		}
		return null;
	}
}