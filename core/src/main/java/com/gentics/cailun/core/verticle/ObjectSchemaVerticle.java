package com.gentics.cailun.core.verticle;

import static com.gentics.cailun.util.JsonUtils.fromJson;
import static com.gentics.cailun.util.JsonUtils.toJson;
import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import io.vertx.ext.apex.Route;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jacpfx.vertx.spring.SpringVerticle;
import org.neo4j.graphdb.Transaction;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gentics.cailun.core.AbstractCoreApiVerticle;
import com.gentics.cailun.core.data.model.ObjectSchema;
import com.gentics.cailun.core.data.model.Project;
import com.gentics.cailun.core.data.model.PropertyType;
import com.gentics.cailun.core.data.model.PropertyTypeSchema;
import com.gentics.cailun.core.data.model.auth.CaiLunPermission;
import com.gentics.cailun.core.data.model.auth.PermissionType;
import com.gentics.cailun.core.data.model.auth.User;
import com.gentics.cailun.core.rest.common.response.GenericMessageResponse;
import com.gentics.cailun.core.rest.schema.request.ObjectSchemaCreateRequest;
import com.gentics.cailun.core.rest.schema.request.ObjectSchemaUpdateRequest;
import com.gentics.cailun.core.rest.schema.response.ObjectSchemaResponse;
import com.gentics.cailun.core.rest.schema.response.PropertyTypeSchemaResponse;
import com.gentics.cailun.error.HttpStatusCodeErrorException;

@Component
@Scope("singleton")
@SpringVerticle
public class ObjectSchemaVerticle extends AbstractCoreApiVerticle {

	protected ObjectSchemaVerticle() {
		super("schemas");
	}

	@Override
	public void registerEndPoints() throws Exception {
		addSchemaProjectHandlers();

		addCreateHandler();
		addReadHandlers();
		addUpdateHandler();
		addDeleteHandler();
	}

	private void addSchemaProjectHandlers() {
		// TODO consumes, produces, document needed permissions?
		Route route = route("/:schemaUuid/projects/:projectUuid").method(POST);
		route.handler(rc -> {
			ObjectSchema schema;
			try (Transaction tx = graphDb.beginTx()) {

				Project project = getObject(rc, "projectUuid", PermissionType.UPDATE);
				schema = getObject(rc, "schemaUuid", PermissionType.READ);

				if (schema.addProject(project)) {
					schema = schemaService.save(schema);
				} else {
					// TODO 200?
				}
				tx.success();
			}
			rc.response().setStatusCode(200);
			rc.response().end(toJson(schemaService.transformToRest(schema)));
		});

		route = route("/:schemaUuid/projects/:projectUuid").method(DELETE);
		route.handler(rc -> {
			ObjectSchema schema;
			try (Transaction tx = graphDb.beginTx()) {
				Project project = getObject(rc, "projectUuid", PermissionType.UPDATE);
				schema = getObject(rc, "schemaUuid", PermissionType.READ);
				if (schema.removeProject(project)) {
					schema = schemaService.save(schema);
				} else {
					// TODO - 200?
				}
				tx.success();
			}
			rc.response().setStatusCode(200);
			rc.response().end(toJson(schemaService.transformToRest(schema)));
		});
	}

	private void addCreateHandler() {
		// TODO add consumes, produces
		Route route = route("/").method(POST);
		route.handler(rc -> {

			ObjectSchemaCreateRequest requestModel = fromJson(rc, ObjectSchemaCreateRequest.class);
			if (StringUtils.isEmpty(requestModel.getName())) {
				// TODO i18n
				throw new HttpStatusCodeErrorException(400, "The name of a schema is mandatory and cannot be omitted.");
			}

			if (StringUtils.isEmpty(requestModel.getProjectUuid())) {
				// TODO i18n
				throw new HttpStatusCodeErrorException(400, "The project uuid is mandatory for schema creation and cannot be omitted.");
			}
			ObjectSchema schema;
			try (Transaction tx = graphDb.beginTx()) {
				Project project = getObjectByUUID(rc, requestModel.getProjectUuid(), PermissionType.CREATE);
				schema = new ObjectSchema(requestModel.getName());
				// TODO set creator
				schema.setDescription(requestModel.getDescription());

				for (PropertyTypeSchemaResponse restPropSchema : requestModel.getPropertyTypeSchemas()) {
					// TODO validate field?
					PropertyTypeSchema propSchema = new PropertyTypeSchema();
					propSchema.setDescription(restPropSchema.getDesciption());
					propSchema.setKey(restPropSchema.getKey());
					PropertyType type = PropertyType.valueOfName(restPropSchema.getType());
					propSchema.setType(type);
					schema.addPropertyTypeSchema(propSchema);
				}
				schema.addProject(project);
				schema = schemaService.save(schema);

				roleService.addCRUDPermissionOnRole(rc, new CaiLunPermission(project, PermissionType.CREATE), schema);

				tx.success();
			}
			schema = schemaService.reload(schema);
			rc.response().setStatusCode(200);
			rc.response().end(toJson(schemaService.transformToRest(schema)));
		});

	}

	private void addUpdateHandler() {
		// TODO consumes, produces
		Route route = route("/:uuid").method(PUT);
		route.handler(rc -> {
			ObjectSchema schema;
			try (Transaction tx = graphDb.beginTx()) {

				schema = getObject(rc, "uuid", PermissionType.UPDATE);
				ObjectSchemaUpdateRequest requestModel = fromJson(rc, ObjectSchemaUpdateRequest.class);

				// Update name
				if (StringUtils.isEmpty(requestModel.getName())) {
					throw new HttpStatusCodeErrorException(400, i18n.get(rc, "error_name_must_be_set"));
				}
				if (!schema.getName().equals(requestModel.getName())) {
					schema.setName(requestModel.getName());
				}

				// Update description
				if (schema.getDescription() != null && (!schema.getDescription().equals(requestModel.getDescription()))) {
					schema.setDescription(requestModel.getDescription());
				}
				// TODO update modification timestamps
				schema = schemaService.save(schema);
				tx.success();
			}

			rc.response().setStatusCode(200);
			rc.response().end(toJson(schemaService.transformToRest(schema)));

		});
	}

	private void addDeleteHandler() {
		// TODO consumes, produces
		Route route = route("/:uuid").method(DELETE);
		route.handler(rc -> {
			String uuid = rc.request().params().get("uuid");
			try (Transaction tx = graphDb.beginTx()) {
				ObjectSchema schema = getObject(rc, "uuid", PermissionType.DELETE);
				schemaService.delete(schema);
				tx.success();
			}
			rc.response().setStatusCode(200);
			rc.response().end(toJson(new GenericMessageResponse(i18n.get(rc, "schema_deleted", uuid))));
		});

	}

	private void addReadHandlers() {
		route("/:uuid").method(GET).handler(rc -> {
			String uuid = rc.request().params().get("uuid");
			if (StringUtils.isEmpty(uuid)) {
				rc.next();
			} else {
				ObjectSchema schema;
				try (Transaction tx = graphDb.beginTx()) {
					schema = getObject(rc, "uuid", PermissionType.READ);
					tx.success();
				}
				rc.response().setStatusCode(200);
				rc.response().end(toJson(schemaService.transformToRest(schema)));
			}
		});

		// produces(APPLICATION_JSON)
		route("/").method(GET).handler(rc -> {
			// TODO handle paging
				Map<String, ObjectSchemaResponse> resultMap = new HashMap<>();
				try (Transaction tx = graphDb.beginTx()) {

					Iterable<ObjectSchema> schemas = schemaService.findAll();
					if (schemas == null) {
						throw new HttpStatusCodeErrorException(500, "No schemas could be loaded");
					}
					for (ObjectSchema schema : schemas) {
						ObjectSchemaResponse restSchema = schemaService.transformToRest(schema);
						resultMap.put(schema.getName(), restSchema);
					}
					tx.success();
				}
				rc.response().end(toJson(resultMap));
			});

	}

}
