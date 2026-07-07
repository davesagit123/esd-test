package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "datasets")
data class DatasetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "data_points",
    foreignKeys = [
        ForeignKey(
            entity = DatasetEntity::class,
            parentColumns = ["id"],
            childColumns = ["datasetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["datasetId"])]
)
data class DataPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val datasetId: Long,
    val tab: Int,
    val label: String,
    val value1: Double,
    val value2: Double? = null
)

@Dao
interface EsdDao {
    @Query("SELECT * FROM datasets ORDER BY id ASC")
    fun getAllDatasets(): Flow<List<DatasetEntity>>

    @Query("SELECT * FROM datasets WHERE id = :id")
    suspend fun getDatasetById(id: Long): DatasetEntity?

    @Query("SELECT * FROM data_points WHERE datasetId = :datasetId ORDER BY tab ASC, id ASC")
    fun getDataPointsForDataset(datasetId: Long): Flow<List<DataPointEntity>>

    @Query("SELECT * FROM data_points WHERE datasetId = :datasetId ORDER BY tab ASC, id ASC")
    suspend fun getDataPointsForDatasetSync(datasetId: Long): List<DataPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDataset(dataset: DatasetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDataPoint(dataPoint: DataPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDataPoints(dataPoints: List<DataPointEntity>)

    @Update
    suspend fun updateDataPoint(dataPoint: DataPointEntity)

    @Query("DELETE FROM data_points WHERE id = :id")
    suspend fun deleteDataPoint(id: Long)

    @Query("DELETE FROM datasets WHERE id = :id")
    suspend fun deleteDataset(id: Long)

    @Query("DELETE FROM data_points WHERE datasetId = :datasetId")
    suspend fun clearDataPointsForDataset(datasetId: Long)
}

@Database(entities = [DatasetEntity::class, DataPointEntity::class], version = 1, exportSchema = false)
abstract class EsdDatabase : RoomDatabase() {
    abstract fun esdDao(): EsdDao

    companion object {
        @Volatile
        private var INSTANCE: EsdDatabase? = null

        fun getDatabase(context: Context): EsdDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EsdDatabase::class.java,
                    "esd_outliers_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
