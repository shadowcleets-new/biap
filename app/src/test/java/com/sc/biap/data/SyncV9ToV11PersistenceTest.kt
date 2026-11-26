package com.sc.biap.data

import com.sc.biap.data.room.ActivityEntity
import com.sc.biap.data.room.BatchEntity
import com.sc.biap.data.room.DeletedActivityEntity
import com.sc.biap.data.room.DeletedCustomFieldEntity
import com.sc.biap.data.room.DeletedDesignEntity
import com.sc.biap.data.room.DeletedJobWorkEntity
import com.sc.biap.data.room.DeletedKarigarEntity
import com.sc.biap.data.room.DeletedOrderEntity
import com.sc.biap.data.room.DesignDao
import com.sc.biap.data.room.DesignEntity
import com.sc.biap.data.room.DesignWithInventories
import com.sc.biap.data.room.GlobalCustomFieldEntity
import com.sc.biap.data.room.JobWorkEntity
import com.sc.biap.data.room.KarigarEntity
import com.sc.biap.data.room.OrderEntity
import com.sc.biap.data.room.SizeInventoryEntity
import com.sc.biap.data.room.UserProfileEntity
import com.sc.biap.data.room.toDomain
import com.sc.biap.data.room.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies v9-v11 sync round-trips (snapshotFromDao + DesignDao.importData with FK cascade safety),
 * sample approval image metadata propagation, upload pointer matching, and bulk attribute tidying.
 */
class SyncV9ToV11PersistenceTest {

    @Test
    fun `buildSnapshotFromDao and importData round-trip all v9 to v11 tables cleanly`() = runTest {
        val dao = FakeDesignDao()

        val design = Design(
            id = "d1",
            name = "Anarkali",
            category = "Salwar Kameez",
            imagePath = "img_10.jpg",
            imageRef = "img_d1_10_aa.enc",
            imageUpdatedAt = 500L,
            imageUpdatedBy = "u1",
            sizeInventories = listOf(SizeInventory("L", stock = 5, price = 150_000L)),
            customFields = mapOf("Top - Fabric" to "Rayon"),
            karigars = listOf("Atul"),
            updatedAt = 600L,
        )
        val batch = Batch(
            id = "b1",
            designId = "d1",
            size = "L",
            number = 1,
            quantity = 10,
            remaining = 5,
            price = 150_000L,
            createdAt = 100L,
            updatedAt = 200L,
            note = "First cut",
        )
        val orphanBatch = batch.copy(id = "b-orphan", designId = "missing-design")
        val karigar = Karigar(id = "k1", name = "Atul", activities = listOf("Printing"), updatedAt = 300L)
        val job = JobWork(
            id = "j1",
            kind = WorkKind.SAMPLE,
            designName = "Sample X",
            karigar = "Atul",
            activity = "Printing",
            sentQuantity = 1,
            sentAt = 400L,
            photoPath = "img_400.jpg",
            photoRef = "img_job_j1_400_bb.enc",
            voicePath = "/files/voice_notes/vn_400.m4a",
            voiceRef = "vn_j1_400_cc.enc",
            updatedAt = 450L,
        )
        val existingProfile = UserProfile(firstName = "Malay", updatedAt = 999L)
        dao.upsertProfile(existingProfile.toEntity())

        val incoming = BusinessIntelligenceData(
            designs = listOf(design),
            batches = listOf(batch, orphanBatch),
            karigars = listOf(karigar),
            deletedKarigars = mapOf("k-old" to 250L),
            activities = mapOf("Printing" to 200L),
            deletedActivities = mapOf("OldWork" to 220L),
            jobWork = listOf(job),
            deletedJobWork = mapOf("j-old" to 260L),
            globalCustomFields = setOf("Top - Fabric"),
            globalCustomFieldsMetadata = mapOf("Top - Fabric" to 100L),
            deletedDesignIds = setOf("d-old"),
            deletedDesigns = mapOf("d-old" to 150L),
            deletedCustomFields = mapOf("Unused" to 160L),
            orders = listOf(Order(id = "o1", createdAt = 50L, lines = emptyList(), updatedAt = 50L)),
            deletedOrders = mapOf("o-old" to 180L),
            profile = null,
        )

        dao.importData(incoming)

        // Batches belonging to live designs survived the deleteAllDesigns() FK cascade; orphan batch was skipped.
        assertEquals(listOf(batch), dao.allBatchesOnce().map { it.toDomain() })
        val snapshot = buildSnapshotFromDao(dao)

        assertEquals(listOf(design), snapshot.designs)
        assertEquals(listOf(batch), snapshot.batches)
        assertEquals(listOf(karigar), snapshot.karigars)
        assertEquals(mapOf("k-old" to 250L), snapshot.deletedKarigars)
        assertEquals(mapOf("Printing" to 200L), snapshot.activities)
        assertEquals(mapOf("OldWork" to 220L), snapshot.deletedActivities)
        assertEquals(listOf(job), snapshot.jobWork)
        assertEquals(mapOf("j-old" to 260L), snapshot.deletedJobWork)
        assertEquals(existingProfile, snapshot.profile)
    }

    @Test
    fun `approvedDesignFromSample copies photoRef, imageUpdatedAt, and imageUpdatedBy when present`() {
        val withRef = JobWork(
            id = "j1",
            kind = WorkKind.SAMPLE,
            designName = "Pink Gota",
            karigar = "Atul",
            sentQuantity = 1,
            sentAt = 100L,
            photoPath = "img_100.jpg",
            photoRef = "img_job_j1_100_aa.enc",
        )
        val createdWithRef = approvedDesignFromSample(
            job = withRef,
            designId = "d-new",
            now = 500L,
            imageUpdatedBy = "uid-owner",
        )
        assertEquals("img_100.jpg", createdWithRef.imagePath)
        assertEquals("img_job_j1_100_aa.enc", createdWithRef.imageRef)
        assertEquals(500L, createdWithRef.imageUpdatedAt)
        assertEquals("uid-owner", createdWithRef.imageUpdatedBy)

        val withoutRef = withRef.copy(photoRef = null)
        val createdWithoutRef = approvedDesignFromSample(
            job = withoutRef,
            designId = "d-new-2",
            now = 500L,
            imageUpdatedBy = "uid-owner",
        )
        assertEquals("img_100.jpg", createdWithoutRef.imagePath)
        assertNull(createdWithoutRef.imageRef)
        assertEquals(0L, createdWithoutRef.imageUpdatedAt)
        assertNull(createdWithoutRef.imageUpdatedBy)
    }

    @Test
    fun `applyUploadedImageToDao matches relative stored path against resolved absolute path`() = runTest {
        val dao = FakeDesignDao()
        dao.insertDesign(
            Design(
                id = "d1",
                name = "Kurti",
                category = "Kurti",
                imagePath = "img_cover.jpg",
                extraImages = listOf(DesignPhoto(localPath = "img_extra.jpg")),
                updatedAt = 100L,
            ).toEntity()
        )

        val coverApplied = applyUploadedImageToDao(
            dao = dao,
            designId = "d1",
            localPath = "/data/user/0/com.sc.biap/files/img_cover.jpg",
            key = "img_d1_200_aa.enc",
            uploadedBy = "u1",
            now = 200L,
        )
        val extraApplied = applyUploadedImageToDao(
            dao = dao,
            designId = "d1",
            localPath = "/data/user/0/com.sc.biap/files/img_extra.jpg",
            key = "img_d1_300_bb.enc",
            uploadedBy = "u1",
            now = 300L,
        )

        assertTrue(coverApplied)
        assertTrue(extraApplied)
        val stored = dao.getDesignById("d1")!!.design
        assertEquals("img_d1_200_aa.enc", stored.imageRef)
        assertEquals("u1", stored.imageUpdatedBy)
        assertEquals("img_d1_300_bb.enc", stored.extraImages.single().ref)
    }

    @Test
    fun `applyUploadedJobMediaToDao updates photoRef and voiceRef and spares deleted jobs`() = runTest {
        val dao = FakeDesignDao()
        dao.insertJobWork(
            JobWork(
                id = "j1",
                kind = WorkKind.ALTERATION,
                designId = "d1",
                size = "L",
                karigar = "Atul",
                sentQuantity = 2,
                sentAt = 100L,
                photoPath = "img_100.jpg",
                voicePath = "/files/voice_notes/vn_100.m4a",
            ).toEntity()
        )

        assertTrue(applyUploadedJobMediaToDao(dao, "j1", isVoice = false, key = "img_job_j1_200_aa.enc"))
        assertTrue(applyUploadedJobMediaToDao(dao, "j1", isVoice = true, key = "vn_j1_250_bb.enc"))
        assertTrue(applyUploadedJobMediaToDao(dao, "missing-job", isVoice = false, key = "img_job_missing.enc"))

        val stored = dao.jobWorkById("j1")!!
        assertEquals("img_job_j1_200_aa.enc", stored.photoRef)
        assertEquals("vn_j1_250_bb.enc", stored.voiceRef)
        assertEquals(100L, stored.updatedAt)
    }

    @Test
    fun `unifyAttributeValue updates active drifted spellings only without touching archived or unrelated fields`() = runTest {
        val dao = FakeDesignDao()
        dao.insertDesign(
            Design(
                id = "d1",
                name = "One",
                category = "K",
                imageRef = "img_keep.enc",
                customFields = mapOf("Top - Fabric" to "rayon", "Top - Color" to "Red"),
                updatedAt = 100L,
            ).toEntity()
        )
        dao.insertDesign(
            Design(
                id = "d2",
                name = "Two",
                category = "K",
                customFields = mapOf("Top - Fabric" to " Rayon "),
                updatedAt = 100L,
            ).toEntity()
        )
        dao.insertDesign(
            Design(
                id = "d3",
                name = "Three",
                category = "K",
                customFields = mapOf("Top - Fabric" to "Rayon"),
                updatedAt = 100L,
            ).toEntity()
        )
        dao.insertDesign(
            Design(
                id = "d4",
                name = "Four",
                category = "K",
                customFields = mapOf("Top - Fabric" to "Cotton"),
                updatedAt = 100L,
            ).toEntity()
        )
        dao.insertDesign(
            Design(
                id = "d5-archived",
                name = "Archived",
                category = "K",
                customFields = mapOf("Top - Fabric" to "rayon"),
                archivedAt = 1L,
                updatedAt = 100L,
            ).toEntity()
        )

        val count = dao.unifyAttributeValue(
            field = "Top - Fabric",
            fromValues = setOf("Rayon", "rayon"),
            toValue = "Rayon",
            now = 900L,
        )

        assertEquals(2, count)
        assertEquals("Rayon", dao.getDesignById("d1")!!.design.customFields["Top - Fabric"])
        assertEquals("Red", dao.getDesignById("d1")!!.design.customFields["Top - Color"])
        assertEquals("img_keep.enc", dao.getDesignById("d1")!!.design.imageRef)
        assertEquals("Rayon", dao.getDesignById("d2")!!.design.customFields["Top - Fabric"])
        assertEquals(100L, dao.getDesignById("d3")!!.design.updatedAt)
        assertEquals("Cotton", dao.getDesignById("d4")!!.design.customFields["Top - Fabric"])
        assertFalse(dao.getDesignById("d4")!!.design.updatedAt == 900L)
        assertEquals("rayon", dao.getDesignById("d5-archived")!!.design.customFields["Top - Fabric"])
        assertEquals(100L, dao.getDesignById("d5-archived")!!.design.updatedAt)
    }

    private class FakeDesignDao : DesignDao {
        private val designs = linkedMapOf<String, DesignEntity>()
        private val inventories = mutableListOf<SizeInventoryEntity>()
        private val batches = linkedMapOf<String, BatchEntity>()
        private val orders = linkedMapOf<String, OrderEntity>()
        private val deletedOrders = linkedMapOf<String, DeletedOrderEntity>()
        private val karigars = linkedMapOf<String, KarigarEntity>()
        private val deletedKarigars = linkedMapOf<String, DeletedKarigarEntity>()
        private val activities = linkedMapOf<String, ActivityEntity>()
        private val deletedActivities = linkedMapOf<String, DeletedActivityEntity>()
        private val jobWork = linkedMapOf<String, JobWorkEntity>()
        private val deletedJobWork = linkedMapOf<String, DeletedJobWorkEntity>()
        private val globals = linkedMapOf<String, GlobalCustomFieldEntity>()
        private val deletedDesigns = linkedMapOf<String, DeletedDesignEntity>()
        private val deletedFields = linkedMapOf<String, DeletedCustomFieldEntity>()
        private var profile: UserProfileEntity? = null

        override fun getAllDesignsFlow(): Flow<List<DesignWithInventories>> =
            flowOf(getDesignsSync())

        private fun getDesignsSync(): List<DesignWithInventories> =
            designs.values.map { d ->
                DesignWithInventories(d, inventories.filter { it.designId == d.id })
            }

        override suspend fun getDesignById(id: String): DesignWithInventories? =
            designs[id]?.let { d -> DesignWithInventories(d, inventories.filter { it.designId == d.id }) }

        override suspend fun insertDesign(design: DesignEntity) {
            designs[design.id] = design
        }

        override suspend fun insertInventories(inventories: List<SizeInventoryEntity>) {
            for (inv in inventories) {
                require(inv.designId in designs) { "FOREIGN KEY constraint failed on size_inventories" }
                this.inventories.removeAll { it.designId == inv.designId && it.size == inv.size }
                this.inventories.add(inv)
            }
        }

        override suspend fun deleteInventoriesForDesign(designId: String) {
            inventories.removeAll { it.designId == designId }
        }

        override suspend fun updateImagePointer(id: String, key: String, updatedAt: Long, updatedBy: String?) {
            designs[id]?.let {
                designs[id] = it.copy(imageRef = key, imageUpdatedAt = updatedAt, imageUpdatedBy = updatedBy)
            }
        }

        override suspend fun getImageRef(id: String): String? = designs[id]?.imageRef

        override suspend fun updateExtraImages(id: String, photos: List<DesignPhoto>, updatedAt: Long) {
            designs[id]?.let {
                designs[id] = it.copy(extraImages = photos, extraImagesUpdatedAt = updatedAt)
            }
        }

        override suspend fun getAllExtraImagesRaw(): List<String> = emptyList()

        override suspend fun setArchived(id: String, archivedAt: Long, changedAt: Long) {
            designs[id]?.let {
                designs[id] = it.copy(archivedAt = archivedAt, archiveUpdatedAt = changedAt)
            }
        }

        override suspend fun deleteDesign(id: String) {
            designs.remove(id)
            inventories.removeAll { it.designId == id }
            batches.entries.removeIf { it.value.designId == id }
        }

        override fun getAllOrdersFlow(): Flow<List<OrderEntity>> = flowOf(orders.values.toList())
        override suspend fun getAllOrdersOnce(): List<OrderEntity> = orders.values.toList()
        override suspend fun getOrderById(id: String): OrderEntity? = orders[id]
        override suspend fun insertOrder(order: OrderEntity) { orders[order.id] = order }
        override suspend fun insertOrders(orders: List<OrderEntity>) { orders.forEach { this.orders[it.id] = it } }
        override suspend fun deleteOrder(id: String) { orders.remove(id) }
        override suspend fun deleteAllOrders() { orders.clear() }
        override fun getAllDeletedOrdersFlow(): Flow<List<DeletedOrderEntity>> = flowOf(deletedOrders.values.toList())
        override suspend fun getAllDeletedOrdersOnce(): List<DeletedOrderEntity> = deletedOrders.values.toList()
        override suspend fun insertDeletedOrder(deleted: DeletedOrderEntity) { deletedOrders[deleted.orderId] = deleted }
        override suspend fun insertDeletedOrders(deletedList: List<DeletedOrderEntity>) {
            deletedList.forEach { deletedOrders[it.orderId] = it }
        }
        override suspend fun deleteAllDeletedOrders() { deletedOrders.clear() }
        override suspend fun deleteDeletedOrderTombstone(orderId: String) { deletedOrders.remove(orderId) }

        override suspend fun decrementStock(designId: String, size: String, qty: Int): Int {
            val idx = inventories.indexOfFirst { it.designId == designId && it.size == size && it.stock >= qty }
            if (idx < 0) return 0
            inventories[idx] = inventories[idx].copy(stock = inventories[idx].stock - qty)
            return 1
        }

        override suspend fun incrementStock(designId: String, size: String, qty: Int): Int {
            val idx = inventories.indexOfFirst { it.designId == designId && it.size == size }
            if (idx < 0) return 0
            inventories[idx] = inventories[idx].copy(stock = inventories[idx].stock + qty)
            return 1
        }

        override suspend fun touchDesign(id: String, now: Long) {
            designs[id]?.let { designs[id] = it.copy(updatedAt = now) }
        }

        override suspend fun setDesignKarigars(id: String, karigars: List<String>, now: Long) {
            designs[id]?.let { designs[id] = it.copy(karigars = karigars, updatedAt = now) }
        }

        override suspend fun setDesignCustomFields(id: String, customFields: Map<String, String>, now: Long) {
            designs[id]?.let { designs[id] = it.copy(customFields = customFields, updatedAt = now) }
        }

        override suspend fun batchesFor(designId: String, size: String): List<BatchEntity> =
            batches.values.filter { it.designId == designId && it.size == size }.sortedBy { it.number }

        override suspend fun allBatchesOnce(): List<BatchEntity> = batches.values.toList()
        override fun allBatchesFlow(): Flow<List<BatchEntity>> = flowOf(batches.values.toList())

        override suspend fun insertBatch(batch: BatchEntity) {
            require(batch.designId in designs) { "FOREIGN KEY constraint failed on batches" }
            batches[batch.id] = batch
        }

        override suspend fun insertBatches(batches: List<BatchEntity>) {
            batches.forEach { insertBatch(it) }
        }

        override suspend fun takeFromBatch(id: String, qty: Int, now: Long) {
            batches[id]?.let { batches[id] = it.copy(remaining = it.remaining - qty, updatedAt = now) }
        }

        override suspend fun returnToBatch(id: String, qty: Int, now: Long) {
            batches[id]?.let { batches[id] = it.copy(remaining = it.remaining + qty, updatedAt = now) }
        }

        override suspend fun emptyBatchesForSize(designId: String, size: String, now: Long) {
            batches.replaceAll { _, b ->
                if (b.designId == designId && b.size == size) b.copy(remaining = 0, updatedAt = now) else b
            }
        }

        override suspend fun emptyBatchesForDesign(designId: String, now: Long) {
            batches.replaceAll { _, b ->
                if (b.designId == designId) b.copy(remaining = 0, updatedAt = now) else b
            }
        }

        override suspend fun zeroStockForSize(designId: String, size: String) {
            inventories.replaceAll { if (it.designId == designId && it.size == size) it.copy(stock = 0) else it }
        }

        override suspend fun zeroStockForDesign(designId: String) {
            inventories.replaceAll { if (it.designId == designId) it.copy(stock = 0) else it }
        }

        override suspend fun addStock(designId: String, size: String, qty: Int) {
            incrementStock(designId, size, qty)
        }

        override fun karigarsFlow(): Flow<List<KarigarEntity>> = flowOf(karigars.values.toList())
        override suspend fun karigarsOnce(): List<KarigarEntity> = karigars.values.toList()
        override suspend fun upsertKarigar(karigar: KarigarEntity) { karigars[karigar.id] = karigar }
        override suspend fun upsertKarigars(karigars: List<KarigarEntity>) {
            karigars.forEach { this.karigars[it.id] = it }
        }
        override suspend fun deleteKarigarRow(id: String) { karigars.remove(id) }
        override suspend fun insertDeletedKarigar(row: DeletedKarigarEntity) { deletedKarigars[row.karigarId] = row }
        override suspend fun deletedKarigarsOnce(): List<DeletedKarigarEntity> = deletedKarigars.values.toList()
        override suspend fun clearDeletedKarigar(id: String) { deletedKarigars.remove(id) }
        override fun activitiesFlow(): Flow<List<ActivityEntity>> = flowOf(activities.values.toList())
        override suspend fun activitiesOnce(): List<ActivityEntity> = activities.values.toList()
        override suspend fun upsertActivity(activity: ActivityEntity) { activities[activity.name] = activity }
        override suspend fun upsertActivities(activities: List<ActivityEntity>) {
            activities.forEach { this.activities[it.name] = it }
        }
        override suspend fun deleteActivityRow(name: String) { activities.remove(name) }
        override suspend fun insertDeletedActivity(row: DeletedActivityEntity) { deletedActivities[row.name] = row }
        override suspend fun deletedActivitiesOnce(): List<DeletedActivityEntity> = deletedActivities.values.toList()

        override fun getProfileFlow(id: Int): Flow<UserProfileEntity?> = flowOf(profile)
        override suspend fun getProfileOnce(id: Int): UserProfileEntity? = profile
        override suspend fun upsertProfile(profile: UserProfileEntity) { this.profile = profile }

        override fun getAllGlobalCustomFieldsFlow(): Flow<List<GlobalCustomFieldEntity>> =
            flowOf(globals.values.toList())
        override suspend fun getAllGlobalCustomFields(): List<GlobalCustomFieldEntity> = globals.values.toList()
        override suspend fun insertGlobalCustomFields(fields: List<GlobalCustomFieldEntity>) {
            fields.forEach { globals[it.field] = it }
        }
        override suspend fun deleteGlobalCustomField(field: String) { globals.remove(field) }

        override fun getAllDeletedDesignsFlow(): Flow<List<DeletedDesignEntity>> =
            flowOf(deletedDesigns.values.toList())
        override suspend fun insertDeletedDesign(deleted: DeletedDesignEntity) {
            deletedDesigns[deleted.designId] = deleted
        }
        override suspend fun insertDeletedDesigns(deletedList: List<DeletedDesignEntity>) {
            deletedList.forEach { deletedDesigns[it.designId] = it }
        }

        override fun getAllDeletedCustomFieldsFlow(): Flow<List<DeletedCustomFieldEntity>> =
            flowOf(deletedFields.values.toList())
        override suspend fun insertDeletedCustomField(deleted: DeletedCustomFieldEntity) {
            deletedFields[deleted.field] = deleted
        }
        override suspend fun insertDeletedCustomFields(deletedList: List<DeletedCustomFieldEntity>) {
            deletedList.forEach { deletedFields[it.field] = it }
        }
        override suspend fun deleteDeletedCustomFieldTombstone(field: String) { deletedFields.remove(field) }

        override suspend fun deleteAllDesigns() {
            designs.clear()
            // Simulate ON DELETE CASCADE from designs -> size_inventories & batches
            inventories.clear()
            batches.clear()
        }
        override suspend fun deleteAllInventories() { inventories.clear() }
        override suspend fun deleteAllBatches() { batches.clear() }
        override suspend fun deleteAllKarigars() { karigars.clear() }
        override suspend fun deleteAllDeletedKarigars() { deletedKarigars.clear() }
        override suspend fun insertDeletedKarigars(deleted: List<DeletedKarigarEntity>) {
            deleted.forEach { deletedKarigars[it.karigarId] = it }
        }
        override suspend fun deleteAllActivities() { activities.clear() }
        override suspend fun deleteAllDeletedActivities() { deletedActivities.clear() }
        override suspend fun insertDeletedActivities(deleted: List<DeletedActivityEntity>) {
            deleted.forEach { deletedActivities[it.name] = it }
        }
        override suspend fun deleteAllJobWork() { jobWork.clear() }
        override suspend fun deleteAllDeletedJobWork() { deletedJobWork.clear() }
        override suspend fun deleteAllGlobalCustomFields() { globals.clear() }
        override suspend fun deleteAllDeletedDesigns() { deletedDesigns.clear() }
        override suspend fun deleteAllDeletedCustomFields() { deletedFields.clear() }
        override suspend fun deleteDeletedDesignTombstone(designId: String) { deletedDesigns.remove(designId) }
        override suspend fun getAllImagePaths(): List<String> = designs.values.mapNotNull { it.imagePath }
        override suspend fun getAllDesignsOnce(): List<DesignWithInventories> = getDesignsSync()
        override suspend fun getAllDeletedDesignsOnce(): List<DeletedDesignEntity> = deletedDesigns.values.toList()
        override suspend fun getAllDeletedCustomFieldsOnce(): List<DeletedCustomFieldEntity> = deletedFields.values.toList()

        override fun allJobWorkFlow(): Flow<List<JobWorkEntity>> = flowOf(jobWork.values.toList())
        override suspend fun allJobWorkOnce(): List<JobWorkEntity> = jobWork.values.toList()
        override suspend fun jobWorkById(id: String): JobWorkEntity? = jobWork[id]
        override suspend fun insertJobWork(job: JobWorkEntity) { jobWork[job.id] = job }
        override suspend fun insertJobWorks(jobs: List<JobWorkEntity>) { jobs.forEach { jobWork[it.id] = it } }
        override suspend fun deleteJobWork(id: String) { jobWork.remove(id) }
        override suspend fun updateJobPhotoRef(id: String, key: String) {
            jobWork[id]?.let { jobWork[id] = it.copy(photoRef = key) }
        }
        override suspend fun updateJobVoiceRef(id: String, key: String) {
            jobWork[id]?.let { jobWork[id] = it.copy(voiceRef = key) }
        }
        override suspend fun getAllJobWorkImagePaths(): List<String> = jobWork.values.mapNotNull { it.photoPath }
        override fun deletedJobWorkFlow(): Flow<List<DeletedJobWorkEntity>> = flowOf(deletedJobWork.values.toList())
        override suspend fun deletedJobWorkOnce(): List<DeletedJobWorkEntity> = deletedJobWork.values.toList()
        override suspend fun insertDeletedJobWork(deleted: DeletedJobWorkEntity) { deletedJobWork[deleted.id] = deleted }
        override suspend fun insertDeletedJobWorks(deleted: List<DeletedJobWorkEntity>) {
            deleted.forEach { deletedJobWork[it.id] = it }
        }
        override suspend fun priceFor(designId: String, size: String): Long? =
            inventories.firstOrNull { it.designId == designId && it.size == size }?.price
        override suspend fun jobWorkForLot(lotId: String): List<JobWorkEntity> =
            jobWork.values.filter { it.lotId == lotId }
    }
}
