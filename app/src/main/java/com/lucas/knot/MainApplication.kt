package com.lucas.knot

import android.app.Application
import android.app.ProgressDialog
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.squareup.sqldelight.android.AndroidSqliteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grpc.ManagedChannelBuilder
import services.*
import javax.inject.Singleton
import services.AdvertisingGrpc.newBlockingStub as newAdvertisingStub
import services.ChatGrpc.newBlockingStub as newChatStub
import services.ChatGrpc.newStub as newChatNormalStub
import services.IdentityGrpc.newBlockingStub as newIdentityStub
import services.NotificationGrpc.newBlockingStub as newNotificationStub
import services.SignalingGrpc.newStub as newSignalingStub
import services.UserGrpc.newBlockingStub as newUserStub

@HiltAndroidApp
class MainApplication : Application()

// setup dependency injection here
@Module
@InstallIn(ApplicationComponent::class)
object NetworkModule {
    private const val baseUrl = "10.0.2.2"

    //TODO TLS
    @Provides
    fun providesIdentityService(): IdentityGrpc.IdentityBlockingStub {
        val channel = ManagedChannelBuilder.forAddress(baseUrl, 5001)
                .usePlaintext()
                .build()
        return newIdentityStub(channel)
    }

    @Provides
    fun providesChatService(): ChatGrpc.ChatBlockingStub {
        val channel = ManagedChannelBuilder.forAddress(baseUrl, 7001)
                .usePlaintext()
                .build()
        return newChatStub(channel)
    }

    @Provides
    fun providesAsyncChatService(): ChatGrpc.ChatStub {
        val channel = ManagedChannelBuilder.forAddress(baseUrl, 7001)
                .usePlaintext()
                .build()
        return newChatNormalStub(channel)
    }

    @Provides
    fun providesUserService(): UserGrpc.UserBlockingStub {
        val channel = ManagedChannelBuilder.forAddress(baseUrl, 8001)
                .usePlaintext()
                .build()
        return newUserStub(channel)
    }

    @Provides
    fun providesNotificationService(): NotificationGrpc.NotificationBlockingStub {
        val channel = ManagedChannelBuilder.forAddress(baseUrl, 11001)
                .usePlaintext()
                .build()
        return newNotificationStub(channel)
    }

    // provide normal stub for streaming
    @Provides
    fun providesSignalingService(): SignalingGrpc.SignalingStub {
        val channel = ManagedChannelBuilder.forAddress(baseUrl, 10001)
                .usePlaintext()
                .build()
        return newSignalingStub(channel)
    }

    @Provides
    fun providesAdvertisingService(): AdvertisingGrpc.AdvertisingBlockingStub {
        val channel = ManagedChannelBuilder.forAddress(baseUrl, 9001)
                .usePlaintext()
                .build()
        return newAdvertisingStub(channel)
    }

    // although firebase classes are singletons, it is nicer to just use constructor injection
    @Provides
    fun providesFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    fun providesFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }

    @Provides
    fun providesFirebaseMessaging(): FirebaseMessaging {
        return FirebaseMessaging.getInstance()
    }
}

@Module
@InstallIn(ApplicationComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideSQLDriver(@ApplicationContext context: Context): AndroidSqliteDriver {
        return AndroidSqliteDriver(Database.Schema, context, "cache.db")
    }

    @Provides
    @Singleton
    fun provideDatabase(androidSqliteDriver: AndroidSqliteDriver): Database {
        return Database(androidSqliteDriver)
    }
}

@Module
@InstallIn(ActivityComponent::class)
object UIComponents {
    @Provides
    fun provideLoadingProgressDialog(@ActivityContext context: Context): ProgressDialog {
        val pd = ProgressDialog(context)
        pd.setTitle(context.getString(R.string.loading_text))
        pd.setMessage(context.getString(R.string.loading_description))
        return pd
    }
}