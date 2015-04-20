package com.gentics.cailun.core.verticle;

import static com.gentics.cailun.demo.DemoDataProvider.PROJECT_NAME;
import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import io.vertx.core.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.neo4j.graphdb.Transaction;
import org.springframework.beans.factory.annotation.Autowired;

import com.gentics.cailun.core.AbstractRestVerticle;
import com.gentics.cailun.core.data.model.Content;
import com.gentics.cailun.core.data.model.auth.PermissionType;
import com.gentics.cailun.core.data.service.ContentService;
import com.gentics.cailun.core.rest.content.request.ContentCreateRequest;
import com.gentics.cailun.core.rest.content.request.ContentUpdateRequest;
import com.gentics.cailun.core.rest.content.response.ContentListResponse;
import com.gentics.cailun.core.rest.content.response.ContentResponse;
import com.gentics.cailun.error.HttpStatusCodeErrorException;
import com.gentics.cailun.test.AbstractRestVerticleTest;
import com.gentics.cailun.util.JsonUtils;

public class ContentVerticleTest extends AbstractRestVerticleTest {

	@Autowired
	private ContentVerticle verticle;

	@Autowired
	private ContentService contentService;

	@Override
	public AbstractRestVerticle getVerticle() {
		return verticle;
	}

	// Create tests

	@Test
	public void testCreateContentWithBogusLanguageCode() throws HttpStatusCodeErrorException, Exception {

		ContentCreateRequest request = new ContentCreateRequest();
		request.setSchemaName("content");
		request.addProperty("english", "filename", "new-page.html");
		request.addProperty("english", "name", "english content name");
		request.addProperty("english", "content", "Blessed mealtime again!");
		request.setTagUuid(data().getNews().getUuid());

		String response = request(info, POST, "/api/v1/" + PROJECT_NAME + "/contents", 400, "Bad Request", JsonUtils.toJson(request));
		expectMessageResponse("error_language_not_found", response, "english");
	}

	@Test
	public void testCreateContent() throws Exception {

		ContentCreateRequest request = new ContentCreateRequest();
		request.setSchemaName("content");
		request.addProperty("en", "filename", "new-page.html");
		request.addProperty("en", "name", "english content name");
		request.addProperty("en", "content", "Blessed mealtime again!");
		request.setTagUuid(data().getNews().getUuid());

		String response = request(info, POST, "/api/v1/" + PROJECT_NAME + "/contents", 200, "OK", JsonUtils.toJson(request));
		test.assertContent(request, JsonUtils.readValue(response, ContentResponse.class));

	}

	@Test
	public void testCreateContentWithMissingTagUuid() throws Exception {

		ContentCreateRequest request = new ContentCreateRequest();
		request.setSchemaName("content");
		request.addProperty("en", "filename", "new-page.html");
		request.addProperty("en", "name", "english content name");
		request.addProperty("en", "content", "Blessed mealtime again!");

		String response = request(info, POST, "/api/v1/" + PROJECT_NAME + "/contents", 400, "Bad Request", JsonUtils.toJson(request));
		expectMessageResponse("content_missing_parenttag_field", response);

	}

	@Test
	public void testCreateContentWithMissingPermission() throws Exception {

		// Revoke create perm
		try (Transaction tx = graphDb.beginTx()) {
			roleService.revokePermission(info.getRole(), data().getNews(), PermissionType.CREATE);
			tx.success();
		}

		ContentCreateRequest request = new ContentCreateRequest();
		request.setSchemaName("content");
		request.addProperty("english", "filename", "new-page.html");
		request.addProperty("english", "name", "english content name");
		request.addProperty("english", "content", "Blessed mealtime again!");
		request.setTagUuid(data().getNews().getUuid());

		String response = request(info, POST, "/api/v1/" + PROJECT_NAME + "/contents", 403, "Forbidden", JsonUtils.toJson(request));
		expectMessageResponse("error_missing_perm", response, data().getNews().getUuid());
	}

	// Read tests

	@Test
	public void testReadContents() throws Exception {

		// Don't grant permissions to the no perm content. We want to make sure that this one will not be listed.
		Content noPermContent = new Content();
		try (Transaction tx = graphDb.beginTx()) {
			noPermContent.setCreator(info.getUser());
			noPermContent = contentService.save(noPermContent);
			tx.success();
		}
		// noPermContent = contentService.reload(noPermContent);
		assertNotNull(noPermContent.getUuid());

		// Test default paging parameters
		String response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/contents/", 200, "OK");
		ContentListResponse restResponse = JsonUtils.readValue(response, ContentListResponse.class);
		assertEquals(25, restResponse.getMetainfo().getPerPage());
		assertEquals(1, restResponse.getMetainfo().getCurrentPage());
		assertEquals(25, restResponse.getData().size());

		int perPage = 11;
		response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/contents/?per_page=" + perPage + "&page=" + 3, 200, "OK");
		restResponse = JsonUtils.readValue(response, ContentListResponse.class);
		assertEquals(perPage, restResponse.getData().size());

		// Extra Contents + permitted content
		int totalContents = data().getTotalContents();
		int totalPages = (int) Math.ceil(totalContents / (double) perPage);
		assertEquals("The response did not contain the correct amount of items", perPage, restResponse.getData().size());
		assertEquals(3, restResponse.getMetainfo().getCurrentPage());
		assertEquals(totalPages, restResponse.getMetainfo().getPageCount());
		assertEquals(perPage, restResponse.getMetainfo().getPerPage());
		assertEquals(totalContents, restResponse.getMetainfo().getTotalCount());

		List<ContentResponse> allContents = new ArrayList<>();
		for (int page = 1; page < totalPages; page++) {
			response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/contents/?per_page=" + perPage + "&page=" + page, 200, "OK");
			restResponse = JsonUtils.readValue(response, ContentListResponse.class);
			allContents.addAll(restResponse.getData());
		}
		assertEquals("Somehow not all users were loaded when loading all pages.", totalContents, allContents.size());

		// Verify that the no_perm_content is not part of the response
		final String noPermContentUUID = noPermContent.getUuid();
		List<ContentResponse> filteredUserList = allContents.parallelStream().filter(restContent -> restContent.getUuid().equals(noPermContentUUID))
				.collect(Collectors.toList());
		assertTrue("The no perm content should not be part of the list since no permissions were added.", filteredUserList.size() == 0);

		response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/contents/?per_page=25&page=-1", 400, "Bad Request");
		expectMessageResponse("error_invalid_paging_parameters", response);
		response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/contents/?per_page=25&page=0", 400, "Bad Request");
		expectMessageResponse("error_invalid_paging_parameters", response);
		response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/contents/?per_page=0&page=1", 400, "Bad Request");
		expectMessageResponse("error_invalid_paging_parameters", response);
		response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/contents/?per_page=-1&page=1", 400, "Bad Request");
		expectMessageResponse("error_invalid_paging_parameters", response);

		response = request(info, HttpMethod.GET, "/api/v1/" + PROJECT_NAME + "/contents/?per_page=25&page=4242", 200, "OK");
		ContentListResponse list = JsonUtils.readValue(response, ContentListResponse.class);
		assertEquals(4242, list.getMetainfo().getCurrentPage());
		assertEquals(0, list.getData().size());
		assertEquals(25, list.getMetainfo().getPerPage());
		assertEquals(2, list.getMetainfo().getPageCount());
		assertEquals(data().getTotalContents(), list.getMetainfo().getTotalCount());

	}

	@Test
	public void testReadContentsWithoutPermissions() throws Exception {

		// TODO add content that has no perms and check the response
		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/contents/", 200, "OK");
		ContentListResponse restResponse = JsonUtils.readValue(response, ContentListResponse.class);

		int nElements = restResponse.getData().size();
		assertEquals("The amount of elements in the list did not match the expected count", 25, nElements);
		assertEquals(0, restResponse.getMetainfo().getCurrentPage());
		assertEquals(2, restResponse.getMetainfo().getPageCount());
		assertEquals(25, restResponse.getMetainfo().getPerPage());
		assertEquals(data().getTotalContents(), restResponse.getMetainfo().getTotalCount());
	}

	@Test
	public void testReadContentByUUID() throws Exception {
		Content content = data().getNews2015Content();
		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/contents/" + content.getUuid(), 200, "OK");
		test.assertContent(content, JsonUtils.readValue(response, ContentResponse.class));
	}

	@Test
	public void testReadContentByUUIDWithDepthParam() throws Exception {
		Content content = data().getNews2015Content();

		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/contents/" + content.getUuid() + "?depth=2", 200, "OK");
		ContentResponse restContent = JsonUtils.readValue(response, ContentResponse.class);
		test.assertContent(content, restContent);
		assertNotNull(restContent.getTags());
		assertEquals(2, restContent.getTags().size());
	}

	@Test
	public void testReadContentByUUIDSingleLanguage() throws Exception {
		Content content = data().getNews2015Content();

		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/contents/" + content.getUuid() + "?lang=de", 200, "OK");
		ContentResponse restContent = JsonUtils.readValue(response, ContentResponse.class);
		test.assertContent(content, restContent);

		assertNull(restContent.getProperties("en"));
		assertNotNull(restContent.getProperties("de"));
	}

	@Test
	public void testReadContentWithBogusLanguageCode() throws Exception {

		Content content = data().getNews2015Content();

		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/contents/" + content.getUuid() + "?lang=blabla,edgsdg", 400, "Bad Request");
		expectMessageResponse("error_language_not_found", response, "blabla");

	}

	@Test
	public void testReadContentByUUIDWithoutPermission() throws Exception {
		Content content = data().getNews2015Content();
		try (Transaction tx = graphDb.beginTx()) {
			roleService.revokePermission(info.getRole(), content, PermissionType.READ);
			tx.success();
		}
		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/contents/" + content.getUuid(), 403, "Forbidden");
		expectMessageResponse("error_missing_perm", response, content.getUuid());
	}

	@Test
	public void testReadContentByBogusUUID() throws Exception {
		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/contents/bogusUUID", 404, "Not Found");
		expectMessageResponse("object_not_found_for_uuid", response, "bogusUUID");
	}

	@Test
	public void testReadContentByInvalidUUID() throws Exception {
		String uuid = "dde8ba06bb7211e4897631a9ce2772f5";
		String response = request(info, GET, "/api/v1/" + PROJECT_NAME + "/contents/" + uuid, 404, "Not Found");
		expectMessageResponse("object_not_found_for_uuid", response, uuid);
	}

	// Update

	@Test
	public void testUpdateContent() throws HttpStatusCodeErrorException, Exception {
		ContentUpdateRequest request = new ContentUpdateRequest();
		request.setSchemaName("content");
		final String newFilename = "new-name.html";
		request.addProperty("en", "filename", newFilename);
		final String newName = "english renamed name";
		request.addProperty("en", "name", newName);
		final String newContent = "english renamed content!";
		request.addProperty("en", "content", newContent);

		String response = request(info, PUT, "/api/v1/" + PROJECT_NAME + "/contents/" + data().getNews2015Content().getUuid() + "?lang=de", 200,
				"OK", JsonUtils.toJson(request));
		ContentResponse restContent = JsonUtils.readValue(response, ContentResponse.class);
		assertEquals(newFilename, restContent.getProperty("en", "filename"));
		assertEquals(newName, restContent.getProperty("en", "name"));
		assertEquals(newContent, restContent.getProperty("en", "content"));
		// TODO verify that the content got updated

	}

	@Test
	public void testUpdateContentWithExtraJson() throws HttpStatusCodeErrorException, Exception {
		ContentUpdateRequest request = new ContentUpdateRequest();
		request.setSchemaName("content");
		final String newFilename = "new-name.html";
		request.addProperty("en", "filename", newFilename);
		final String newName = "english renamed name";
		request.addProperty("en", "name", newName);
		final String newContent = "english renamed content!";
		request.addProperty("en", "content", newContent);

		Content content = data().getNews2015Content();
		String json = JsonUtils.toJson(request);
		String response = request(info, PUT, "/api/v1/" + PROJECT_NAME + "/contents/" + content.getUuid() + "?lang=de", 200, "OK", json);
		ContentResponse restContent = JsonUtils.readValue(response, ContentResponse.class);
		assertEquals(newFilename, restContent.getProperty("en", "filename"));
		assertEquals(newName, restContent.getProperty("en", "name"));
		assertEquals(newContent, restContent.getProperty("en", "content"));

		// Reload and update
		try (Transaction tx = graphDb.beginTx()) {
			content = contentService.reload(content);
			assertEquals(newFilename, contentService.getFilename(content, data().getEnglish()));
			assertEquals(newName, contentService.getName(content, data().getEnglish()));
			assertEquals(newContent, contentService.getContent(content, data().getEnglish()));
			tx.success();
		}

	}

	// Delete

	@Test
	public void testDeleteContent() throws Exception {

		Content content = data().getNews2015Content();
		String response = request(info, DELETE, "/api/v1/" + PROJECT_NAME + "/contents/" + content.getUuid(), 200, "OK");
		expectMessageResponse("content_deleted", response, content.getUuid());
		assertNull(contentService.findByUUID(content.getUuid()));
	}

	@Test
	public void testDeleteContentWithNoPerm() throws Exception {

		Content content = data().getNews2015Content();
		try (Transaction tx = graphDb.beginTx()) {
			roleService.revokePermission(info.getRole(), content, PermissionType.DELETE);
			tx.success();
		}

		String response = request(info, DELETE, "/api/v1/" + PROJECT_NAME + "/contents/" + content.getUuid(), 403, "Forbidden");
		expectMessageResponse("error_missing_perm", response, content.getUuid());

		assertNotNull(contentService.findByUUID(content.getUuid()));
	}

}
