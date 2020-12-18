package com.lucas.knot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import services.AdvertisingGrpc
import services.AdvertisingOuterClass
import javax.inject.Inject
import javax.inject.Singleton

data class Advert(val title: String, val imageUrl: String, val message: String)

@Singleton
class AdvertisingRepository @Inject constructor(private val advertisingStub: AdvertisingGrpc.AdvertisingBlockingStub) {
    suspend fun getAdvert() = withContext(Dispatchers.IO) {
        val request = AdvertisingOuterClass.GetAdvertsRequest.newBuilder()
            .build()
        val result = advertisingStub.getAdvertisements(request)
        // pick a random one and return it
        result.advertList.map {
            Advert(it.title, it.imageUrl, it.message)
        }.random()
    }
}