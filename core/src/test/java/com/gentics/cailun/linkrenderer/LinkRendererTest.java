package com.gentics.cailun.linkrenderer;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

import javax.transaction.NotSupportedException;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;
import org.springframework.beans.factory.annotation.Autowired;

import com.gentics.cailun.core.data.model.Content;
import com.gentics.cailun.core.data.model.Language;
import com.gentics.cailun.core.data.service.ContentService;
import com.gentics.cailun.core.link.CaiLunLinkResolver;
import com.gentics.cailun.core.link.CaiLunLinkResolverFactoryImpl;
import com.gentics.cailun.core.link.LinkReplacer;
import com.gentics.cailun.test.AbstractDBTest;

public class LinkRendererTest extends AbstractDBTest {

	final String content = "some bla START<a href=\"${Page(2)}\">Test</a>   dasasdg <a href=\"${Page(3)}\">Test</a>DEN";

	@Autowired
	private CaiLunLinkResolverFactoryImpl<CaiLunLinkResolver> resolverFactory;

	@Autowired
	private ContentService contentService;

	@Before
	public void setup() throws Exception {
		setupData();
	}

	@Test
	public void testNodeReplace() throws IOException, InterruptedException, ExecutionException, NotSupportedException {

		Language german = data().getGerman();
		Language english = data().getEnglish();

		// Create some dummy content
		Content content = new Content();
		try (Transaction tx = graphDb.beginTx()) {
			contentService.setName(content, german, "german name");
			contentService.setFilename(content, german, "german.html");
			contentService.save(content);
			tx.success();
		}

		Content content2 = new Content();
		try (Transaction tx = graphDb.beginTx()) {
			contentService.setName(content2, english, "content 2 english");
			contentService.setFilename(content2, english, "english.html");
			contentService.save(content2);
			tx.success();
		}

		LinkReplacer<CaiLunLinkResolver> replacer = new LinkReplacer(resolverFactory);
		String out = replacer.replace("dgasd");
		System.out.println(out);
	}

	@Ignore("Disabled for now")
	@Test
	public void testRendering() throws IOException {
		System.out.println(content);
		int start = content.indexOf("#");
		int stop = content.lastIndexOf(")");
		int len = stop - start;
		System.out.println("from " + start + " to " + stop + " len " + len);
		InputStream in = IOUtils.toInputStream(content);
		try {
			int e = 0;
			for (int c; (c = in.read()) != -1 /* EOF */;) {
				if (e >= start && e < stop) {
					// System.out.println("skipping");
					in.skip(len);
					System.out.print("Hugsdibugsdi");
					e = stop;
				} else {
					System.out.print((char) c);
				}
				e++;
			}
		} finally {
			in.close();
		}
	}

}
