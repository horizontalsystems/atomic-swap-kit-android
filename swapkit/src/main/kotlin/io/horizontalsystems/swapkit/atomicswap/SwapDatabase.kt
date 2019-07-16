package io.horizontalsystems.swapkit.atomicswap

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Swap::class], version = 1)
abstract class SwapDatabase : RoomDatabase() {
    abstract val swapDao: SwapDao

    companion object {
        fun getInstance(context: Context, dbName: String): SwapDatabase {
            return Room.databaseBuilder(context, SwapDatabase::class.java, dbName)
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
        }
    }
}
