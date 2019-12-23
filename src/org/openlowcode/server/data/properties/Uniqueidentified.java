/********************************************************************************
 * Copyright (c) 2019 [Open Lowcode SAS](https://openlowcode.com/)
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0 .
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package org.openlowcode.server.data.properties;

import java.util.ArrayList;

import java.util.logging.Logger;

import org.openlowcode.tools.misc.NamedList;
import org.openlowcode.module.system.data.sequence.ObjectidseedSequence;
import org.openlowcode.server.data.DataObject;
import org.openlowcode.server.data.DataObjectPayload;
import org.openlowcode.server.data.DataObjectProperty;
import org.openlowcode.server.data.formula.DataUpdateTrigger;
import org.openlowcode.server.data.formula.TriggerLauncher;
import org.openlowcode.server.data.storage.AndQueryCondition;
import org.openlowcode.server.data.storage.QueryCondition;
import org.openlowcode.server.data.storage.StoredField;

/**
 * The unique identified property adds to an object a unique id generated by the
 * server. Objects with the unique identified property can be queried one by
 * one. All objects except potentially some logs that are persisted should have
 * a Uniqueidentified property
 * 
 * @author <a href="https://openlowcode.com/" rel="nofollow">Open Lowcode
 *         SAS</a>
 *
 * @param <E>
 */
public class Uniqueidentified<E extends DataObject<E>> extends DataObjectProperty<E> {
	private static Logger logger = Logger.getLogger(Uniqueidentified.class.getName());
	private StoredField<String> idfield;
	private Storedobject<E> storedobject;

	private static long currentseed = -1;
	private static long currentincrement = -1;

	private static final long MAX_INCREMENT = 1024;

	/**
	 * a central method to the server that will generate unique numbers. It will use
	 * the result of a sequence in the database, to get the unique number base, and
	 * will add a memory generated suffix between 1 and 1024. The method can support
	 * several servers connecting in parallel to the same database for horizontal
	 * stability
	 * 
	 * @return the next unique id
	 */
	protected static synchronized long getNextId() {
		// reinitiates new seed if never initiated or if increment for seed is over.
		if ((currentseed == -1) || (currentincrement == MAX_INCREMENT)) {
			currentseed = ObjectidseedSequence.get().getNextValue();
			currentincrement = 0;
		}
		long returnvalue = currentseed * 1024 + currentincrement;
		currentincrement++;
		return returnvalue;
	}

	/**
	 * @return the definition for this property
	 */
	public UniqueidentifiedDefinition<E> getDefinition() {
		return (UniqueidentifiedDefinition<E>) this.definition;
	}

	/**
	 * Creates the property for an object
	 * 
	 * @param definition    definition
	 * @param parentpayload the payload of the parent data object
	 */
	@SuppressWarnings("unchecked")
	public Uniqueidentified(UniqueidentifiedDefinition<E> definition, DataObjectPayload parentpayload) {
		super(definition, parentpayload);

		idfield = (StoredField<String>) this.field.lookupOnName("ID");

	}

	/**
	 * @return the unique id of the object
	 */
	public DataObjectId<E> getId() {

		return new DataObjectId<E>(this.idfield.getPayload(), definition.getParentObject());
	}

	protected void SetId(String id) {
		this.idfield.setPayload(id);

	}

	/**
	 * performs an efficient update of several objects
	 * 
	 * @param objectbatch           the list of objects
	 * @param uniqueidentifiedbatch their unique identified property (has to be the
	 *                              same size than the object batch)
	 */
	public static <E extends DataObject<E> & UniqueidentifiedInterface<E>> void update(E[] objectbatch,
			Uniqueidentified<E>[] uniqueidentifiedbatch) {
		if (objectbatch == null)
			throw new RuntimeException("cannot treat null array");
		if (uniqueidentifiedbatch == null)
			throw new RuntimeException("cannot treat null array of uniqueidentified");
		QueryCondition[] conditions = new QueryCondition[objectbatch.length];
		DataObjectPayload[] payloads = new DataObjectPayload[objectbatch.length];
		for (int i = 0; i < objectbatch.length; i++) {
			Uniqueidentified<E> uniqueidentified = uniqueidentifiedbatch[i];
			QueryCondition objectuniversalcondition = uniqueidentified.definition.getParentObject()
					.getUniversalQueryCondition(uniqueidentified.definition, null);
			QueryCondition uniqueidcondition = UniqueidentifiedQueryHelper.getIdQueryCondition(null,
					uniqueidentified.getId().getId(), uniqueidentified.definition.getParentObject());
			QueryCondition finalcondition = uniqueidcondition;
			if (objectuniversalcondition != null) {
				finalcondition = new AndQueryCondition(objectuniversalcondition, uniqueidcondition);
			}
			payloads[i] = uniqueidentified.parentpayload;
			conditions[i] = finalcondition;
		}
		DataObjectPayload.massiveupdate(payloads, conditions);
	}

	/**
	 * persists all changes done to this object in-memory version into the
	 * persistence layer
	 * 
	 * @param object the object to update
	 */
	public void update(E object) {
		QueryCondition objectuniversalcondition = definition.getParentObject().getUniversalQueryCondition(definition,
				null);
		QueryCondition uniqueidcondition = UniqueidentifiedQueryHelper.getIdQueryCondition(null, this.getId().getId(),
				definition.getParentObject());
		QueryCondition finalcondition = uniqueidcondition;
		if (objectuniversalcondition != null) {
			finalcondition = new AndQueryCondition(objectuniversalcondition, uniqueidcondition);
		}
		NamedList<DataUpdateTrigger<E>> triggers = object.getDataUpdateTriggers();
		TriggerLauncher<E> triggerlauncher = new TriggerLauncher<E>(triggers);
		triggerlauncher.executeTriggerList(object);

		parentpayload.update(finalcondition);

	}

	/**
	 * performs a similar action to the update method, except it will be registered
	 * as a refresh, typically meaning the business data was not changed manually by
	 * the user. Has a distinct list of triggers from the update
	 * 
	 * @param object the object to refresh
	 */
	public void refresh(E object) {
		QueryCondition objectuniversalcondition = definition.getParentObject().getUniversalQueryCondition(definition,
				null);
		QueryCondition uniqueidcondition = UniqueidentifiedQueryHelper.getIdQueryCondition(null, this.getId().getId(),
				definition.getParentObject());
		QueryCondition finalcondition = uniqueidcondition;
		if (objectuniversalcondition != null) {
			finalcondition = new AndQueryCondition(objectuniversalcondition, uniqueidcondition);
		}
		NamedList<DataUpdateTrigger<E>> triggers = object.getDataRefreshTriggers();
		TriggerLauncher<E> triggerlauncher = new TriggerLauncher<E>(triggers);
		triggerlauncher.executeTriggerList(object);
		parentpayload.update(finalcondition);

	}

	/**
	 * this method will generate the id before insertion
	 * 
	 * @param object the object
	 */
	public void preprocStoredobjectInsert(E object) {
		if (this.idfield.getPayload() != null)
			if (this.idfield.getPayload().length() > 0)
				throw new RuntimeException("Try to insert an already persisted object " + object.getName() + " ID = "
						+ this.idfield.getPayload());
		long idnumber = Uniqueidentified.getNextId();
		// first char in screen indicates the algorithm. Before Open Lowcode v0.33,
		// string started by 1 as algorithm based on getTime
		// 2 indicates an algorithm based on the integer sequence seed (2 billion values
		// for seed + 1024 per round). Encoding in hex as more readable than base64
		String idstring = "2" + Long.toHexString(idnumber);
		// NOTE: due to path algorithm, it is assumed the id only contains letters and
		// figures (especially "[", "]" and "/" are forbidden

		this.SetId(idstring);

	}

	/**
	 * This method allows to set the dependent property stored object. It may be
	 * used by some methods of this property
	 * 
	 * @param storedobject the dependent stored object property
	 */
	public void setDependentPropertyStoredobject(Storedobject<E> storedobject) {
		this.storedobject = storedobject;

	}

	/**
	 * Deletes the object. The object is removed from the persistence layer.
	 * However, a trace is put in the logs for further reference (at severe level)
	 * 
	 * @param object the object to delete
	 */
	public void delete(E object) {
		logger.severe("DELETING OBJECT ID=" + this.getId().getId() + ", " + this.parentpayload.dropPayloadObjectList());
		QueryCondition objectuniversalcondition = definition.getParentObject().getUniversalQueryCondition(definition,
				null);
		QueryCondition uniqueidcondition = UniqueidentifiedQueryHelper.getIdQueryCondition(null, this.getId().getId(),
				definition.getParentObject());
		QueryCondition finalcondition = uniqueidcondition;
		if (objectuniversalcondition != null) {
			finalcondition = new AndQueryCondition(objectuniversalcondition, uniqueidcondition);
		}
		parentpayload.delete(finalcondition);

	}

	/**
	 * generates by batch unique id for the object before they are inserted
	 * 
	 * @param object                       a batch of object
	 * @param preprocuniqueidentifiedbatch the corresponding unique identified
	 *                                     properties
	 */
	public static <E extends DataObject<E>> void preprocStoredobjectInsert(E[] object,
			Uniqueidentified<E>[] preprocuniqueidentifiedbatch) {

		if (object == null)
			throw new RuntimeException("object batch is null");
		if (preprocuniqueidentifiedbatch == null)
			throw new RuntimeException("creationlog batch is null");
		if (object.length != preprocuniqueidentifiedbatch.length)
			throw new RuntimeException("Object batch length " + object.length
					+ " is not consistent with creationlog batch length " + preprocuniqueidentifiedbatch.length);

		// batch control that it is first insertion. Else, the entire batch fails.
		for (int i = 0; i < preprocuniqueidentifiedbatch.length; i++) {
			Uniqueidentified<E> thisuniqueidentified = preprocuniqueidentifiedbatch[i];
			if (thisuniqueidentified.idfield.getPayload() != null)
				if (thisuniqueidentified.idfield.getPayload().length() > 0)
					throw new RuntimeException("Try to insert an already persisted object " + object[0].getName()
							+ " inside batch, ID = " + thisuniqueidentified.idfield.getPayload());
		}
		// batch control that it is first insertion. Else, the entire batch fails.
		for (int i = 0; i < preprocuniqueidentifiedbatch.length; i++) {
			Uniqueidentified<E> thisuniqueidentified = preprocuniqueidentifiedbatch[i];
			thisuniqueidentified.preprocStoredobjectInsert(object[i]);
		}

	}

	/**
	 * @return the related property stored objzect
	 */
	public Storedobject<E> getRelatedStoredobject() {
		return this.storedobject;
	}

	/**
	 * performs a delete by batch
	 * 
	 * @param object                         an array of object
	 * @param uniqueidentifiedarrayformethod their corresponding unique identified
	 *                                       properties
	 */
	public static <E extends DataObject<E>> void delete(E[] object,
			Uniqueidentified<E>[] uniqueidentifiedarrayformethod) {
		if (object == null)
			throw new RuntimeException("cannot treat null array");
		if (uniqueidentifiedarrayformethod == null)
			throw new RuntimeException("cannot treat null array of uniqueidentified");
		if (object.length != uniqueidentifiedarrayformethod.length)
			throw new RuntimeException("Uniqueidentified Array and Object Array do not have same size");
		StringBuffer deleteobjectlog = new StringBuffer();
		ArrayList<QueryCondition> conditionlist = new ArrayList<QueryCondition>();
		ArrayList<DataObjectPayload> payloadlist = new ArrayList<DataObjectPayload>();
		for (int i = 0; i < object.length; i++) {

			Uniqueidentified<E> uniqueidentified = uniqueidentifiedarrayformethod[i];
			deleteobjectlog.append("DELETING OBJECT ID=" + uniqueidentified.getId().getId() + ", "
					+ uniqueidentified.parentpayload.dropPayloadObjectList() + "\n");
			if (i % 100 == 99) {
				logger.severe(deleteobjectlog.toString());
				deleteobjectlog = new StringBuffer();
			}
			QueryCondition objectuniversalcondition = uniqueidentified.definition.getParentObject()
					.getUniversalQueryCondition(uniqueidentified.definition, null);
			QueryCondition uniqueidcondition = UniqueidentifiedQueryHelper.getIdQueryCondition(null,
					uniqueidentified.getId().getId(), uniqueidentified.definition.getParentObject());
			QueryCondition finalcondition = uniqueidcondition;
			if (objectuniversalcondition != null) {
				finalcondition = new AndQueryCondition(objectuniversalcondition, uniqueidcondition);
			}
			conditionlist.add(finalcondition);
			payloadlist.add(uniqueidentified.parentpayload);
		}
		logger.severe(deleteobjectlog.toString());

		DataObjectPayload.massivedelete(payloadlist.toArray(new DataObjectPayload[0]),
				conditionlist.toArray(new QueryCondition[0]));

	}

}