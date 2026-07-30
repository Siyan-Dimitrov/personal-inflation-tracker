package com.siyandimitrov.pocketindex.data.local

import androidx.room.TypeConverter

class RoomConverters {
    @TypeConverter
    fun receiptStatusToString(value: ReceiptStatus): String = value.name

    @TypeConverter
    fun stringToReceiptStatus(value: String): ReceiptStatus = ReceiptStatus.valueOf(value)

    @TypeConverter
    fun unitTypeToString(value: UnitType): String = value.name

    @TypeConverter
    fun stringToUnitType(value: String): UnitType = UnitType.valueOf(value)

    @TypeConverter
    fun observationSourceToString(value: ObservationSource): String = value.name

    @TypeConverter
    fun stringToObservationSource(value: String): ObservationSource =
        ObservationSource.valueOf(value)

    @TypeConverter
    fun recurringCadenceToString(value: RecurringCadence): String = value.name

    @TypeConverter
    fun stringToRecurringCadence(value: String): RecurringCadence =
        RecurringCadence.valueOf(value)
}
