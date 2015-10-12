package com.gentics.mesh.search;

import static com.gentics.mesh.util.MeshAssert.assertSuccess;
import static com.gentics.mesh.util.MeshAssert.latchFor;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gentics.mesh.api.common.PagingInfo;
import com.gentics.mesh.core.AbstractWebVerticle;
import com.gentics.mesh.core.rest.schema.SchemaListResponse;
import com.gentics.mesh.core.rest.schema.SchemaResponse;
import com.gentics.mesh.core.verticle.schema.SchemaVerticle;
import com.gentics.mesh.util.MeshAssert;

import io.vertx.core.Future;

public class SchemaSearchVerticleTest extends AbstractSearchVerticleTest {

	@Autowired
	private SchemaVerticle schemaVerticle;

	@Override
	public List<AbstractWebVerticle> getVertices() {
		List<AbstractWebVerticle> list = new ArrayList<>();
		list.add(searchVerticle);
		list.add(schemaVerticle);
		return list;
	}

	@Test
	public void testSearchSchema() throws InterruptedException, JSONException {
		fullIndex();

		Future<SchemaListResponse> future = getClient().searchSchemas(getSimpleQuery("folder"), new PagingInfo().setPage(1).setPerPage(2));
		latchFor(future);
		assertSuccess(future);
		SchemaListResponse response = future.result();
		assertEquals(1, response.getData().size());

		future = getClient().searchSchemas(getSimpleQuery("blub"), new PagingInfo().setPage(1).setPerPage(2));
		latchFor(future);
		assertSuccess(future);
		response = future.result();
		assertEquals(0, response.getData().size());

		future = getClient().searchSchemas(getSimpleTermQuery("name", "content"), new PagingInfo().setPage(1).setPerPage(2));
		latchFor(future);
		assertSuccess(future);
		response = future.result();
		assertEquals(1, response.getData().size());
	}

	@Test
	@Override
	public void testDocumentCreation() throws Exception {
		final String newName = "newSchema";
		SchemaResponse schema = createSchema(newName);
		MeshAssert.assertElement(boot.schemaContainerRoot(), schema.getUuid(), true);
		Future<SchemaListResponse> future = getClient().searchSchemas(getSimpleTermQuery("name", newName), new PagingInfo().setPage(1).setPerPage(2));
		latchFor(future);
		assertSuccess(future);
		SchemaListResponse response = future.result();
		assertEquals(1, response.getData().size());
	}

	@Test
	@Override
	public void testDocumentDeletion() throws InterruptedException, JSONException {
		final String schemaName = "newSchemaName";
		SchemaResponse schema = createSchema(schemaName);

		Future<SchemaListResponse> future = getClient().searchSchemas(getSimpleTermQuery("name", schemaName),
				new PagingInfo().setPage(1).setPerPage(2));
		latchFor(future);
		assertSuccess(future);
		assertEquals(1, future.result().getData().size());

		deleteSchema(schema.getUuid());
		future = getClient().searchSchemas(getSimpleTermQuery("name", schemaName), new PagingInfo().setPage(1).setPerPage(2));
		latchFor(future);
		assertSuccess(future);
		assertEquals(0, future.result().getData().size());
	}

	@Test
	@Override
	public void testDocumentUpdate() throws InterruptedException, JSONException {
		final String schemaName = "newproject";
		SchemaResponse project = createSchema(schemaName);

		String newSchemaName = "updatedprojectname";
		updateSchema(project.getUuid(), newSchemaName);

		Future<SchemaListResponse> future = getClient().searchSchemas(getSimpleTermQuery("name", schemaName),
				new PagingInfo().setPage(1).setPerPage(2));
		latchFor(future);
		assertSuccess(future);
		assertEquals(0, future.result().getData().size());

		future = getClient().searchSchemas(getSimpleTermQuery("name", newSchemaName), new PagingInfo().setPage(1).setPerPage(2));
		latchFor(future);
		assertSuccess(future);
		assertEquals(1, future.result().getData().size());
	}
}
