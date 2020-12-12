package com.lucas.knot

import android.app.Application
import android.app.ProgressDialog
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
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
import services.ChatGrpc
import services.IdentityGrpc
import services.UserGrpc
import javax.inject.Singleton
import services.ChatGrpc.newBlockingStub as newChatStub
import services.ChatGrpc.newStub as newChatNormalStub
import services.IdentityGrpc.newBlockingStub as newIdentityStub
import services.UserGrpc.newBlockingStub as newUserStub

@HiltAndroidApp
class MainApplication : Application()

// setup dependency injection here
@Module
@InstallIn(ApplicationComponent::class)
object NetworkModule {
    private const val baseUrl = "10.0.2.2"

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
    fun providdesAsyncChatService(): ChatGrpc.ChatStub {
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

    // although firebase classes are singletons, it is nicer to just use constructor injection
    @Provides
    fun providesFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    fun providesFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
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