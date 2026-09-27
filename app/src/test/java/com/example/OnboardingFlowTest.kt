package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingFlowTest {

    private lateinit var context: Context
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        userPreferencesRepository = UserPreferencesRepository(context)
        // Ensure clean state before each test
        userPreferencesRepository.clearUserSession()
    }

    @Test
    fun `initial setup state must be false and user credentials null`() = runBlocking {
        val isCompleted = userPreferencesRepository.isSetupCompletedFlow.first()
        val userEmail = userPreferencesRepository.userEmailFlow.first()

        assertFalse("Setup completion must be false on first run", isCompleted)
        assertNull("User email must be null on first run without demo data", userEmail)
    }

    @Test
    fun `completing setup persists setupCompleted and user profile data`() = runBlocking {
        userPreferencesRepository.setSetupCompleted(true)
        userPreferencesRepository.setUserEmail("user@example.com")
        userPreferencesRepository.setUserName("Test User")

        val isCompleted = userPreferencesRepository.isSetupCompletedFlow.first()
        val email = userPreferencesRepository.userEmailFlow.first()
        val name = userPreferencesRepository.userNameFlow.first()

        assertTrue("Setup should be marked as completed", isCompleted)
        assertEquals("user@example.com", email)
        assertEquals("Test User", name)
    }

    @Test
    fun `logging out resets setup status and clears credentials`() = runBlocking {
        userPreferencesRepository.setSetupCompleted(true)
        userPreferencesRepository.setUserEmail("user@example.com")
        
        userPreferencesRepository.clearUserSession()

        val isCompleted = userPreferencesRepository.isSetupCompletedFlow.first()
        val email = userPreferencesRepository.userEmailFlow.first()

        assertFalse("Setup must be false after clearUserSession", isCompleted)
        assertNull("User email must be null after clearUserSession", email)
    }
}

