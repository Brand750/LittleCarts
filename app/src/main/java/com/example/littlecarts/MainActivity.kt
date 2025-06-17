package com.example.littlecarts

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.Query
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.*
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


val LittleCartsPink = Color(0xFFF6CEE7)
val LittleCartsTextColor = Color(0xFF374151)
val LemonChiffon = Color( 0xFFFFFBE6)



// Data class for items
data class ProductItem(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val category: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val stock: Int = 0,
    val isActive: Boolean = true,
    val createdAt: com.google.firebase.Timestamp? = null
)


data class CartItem(
    val productId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 1,
    val imageUrl: String = ""
)

data class Cart(
    val userId: String = "",
    val items: List<CartItem> = emptyList(),
    val totalPrice: Double = 0.0
)


data class OrderItem(
    val productId: String = "",
    val name: String = "",
    val priceAtPurchase: Double = 0.0,
    val quantity: Int = 0,
    val imageUrl: String = ""
)

data class Order(
    val orderId: String = "",
    val userId: String = "",
    val orderDate: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val totalAmount: Double = 0.0,
    val shippingAddress: String = "",
    val items: List<OrderItem> = emptyList()
)




class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LittleCartsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LittleCartsApp()
                }
            }
        }
    }
}

// Updated NavHost with Firebase integration
@Composable
fun LittleCartsApp() {
    val navController = rememberNavController()

    // Firebase instances
    val auth = Firebase.auth
    val db = Firebase.firestore

    val currentUser = auth.currentUser

    var currentCartItems by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var currentTotal by remember { mutableStateOf(0.0) }
    val currentUserId = getCurrentUserId()

    // Check if user is already logged in
    LaunchedEffect(key1 = true) {
        if (auth.currentUser != null) {
            // Langsung ke Lost Items Screen (Ga perlu login ulang)
            navController.navigate("homepage") {
                popUpTo("opening_page") { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = "opening_page") {
        composable("opening_page") {
            SplashScreen(navController = navController)
        }
        composable("sign_up") {
            SignUpScreen(navController = navController, auth = auth, db = db)
        }
        composable("registration_success") {
            RegistrationSuccessScreen(navController = navController)
        }
        composable("sign_in") {
            SignInScreen(navController = navController, auth = auth)
        }
        composable("signing_in") {
            SigningInScreen(navController = navController)
        }
        composable("upload_item") {
            UploadItemScreen(navController)
        }
        composable("homepage") {
            HomepageScreen(navController = navController)
        }
        composable("about_us") {
            AboutUsScreen(navController = navController)
        }
        composable("profile") {
            ProfileScreen(navController = navController, userId = getCurrentUserId())
        }

        composable("shopping_cart") {
            ShoppingCartScreen(
                navController = navController,
                userId = currentUserId,
                onCheckout = { cartItems, total ->
                    // Save data and navigate
                    currentCartItems = cartItems
                    currentTotal = total
                    navController.navigate("checkout")
                }
            )
        }
        composable("checkout") {
            CheckOutScreen(
                navController = navController,
                totalAmount = currentTotal,
                itemCount = currentCartItems.size,
                userId = currentUserId,
                cartItems = currentCartItems
            )
        }
    }
}


@Composable
fun SplashScreen(navController: NavController) {
    // This will track if we should navigate away from splash screen
    val navigateToSignUp = remember { mutableStateOf(false) }

    // Use LaunchedEffect to start a coroutine that will wait 5 seconds
    LaunchedEffect(key1 = true) {
        delay(5000) // Wait for 5 seconds
        navigateToSignUp.value = true
    }

    // Check if we should navigate to sign up screen
    if (navigateToSignUp.value) {
        LaunchedEffect(key1 = true) {
            navController.navigate("sign_up") {
                // Clear back stack so user can't go back to splash screen
                popUpTo("opening_page") { inclusive = true }
            }
        }
    }

    // Display the opening page / splash screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFBE6)) // Cream background color
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LittleCartsLogo()
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(navController: NavController, auth: FirebaseAuth, db: FirebaseFirestore) {
    // Direct state variables like in the third code
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD5E8FF)) // Light blue background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with logo
            LittleCartsLogo()
            Spacer(modifier = Modifier.height(16.dp))

            // Sign Up Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFF8E3FF)) // Light purple background
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Sign Up",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )

                            Button(
                                onClick = { navController.navigate("sign_in") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFFBE6)
                                ),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .height(40.dp)
                            ) {
                                Text(
                                    text = "Sign In",
                                    color = Color.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Register your account!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Email",
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                containerColor = Color.White
                            )
                        )

                        // Password field
                        Text(
                            text = "Password",
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                containerColor = Color.White
                            ),
                            visualTransformation = PasswordVisualTransformation()
                        )

                        // Confirm Password field
                        Text(
                            text = "Confirm Password",
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                containerColor = Color.White
                            ),
                            visualTransformation = PasswordVisualTransformation()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Display error message if any
                        if (errorMessage.isNotEmpty()) {
                            Text(
                                text = errorMessage,
                                color = Color.Red,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Sign Up button with Firebase integration
                        Button(
                            onClick = {
                                if (username.isBlank() || password.isBlank()) {
                                    errorMessage = "Please fill in all required fields"
                                    return@Button
                                }

                                if (password != confirmPassword) {
                                    errorMessage = "Passwords do not match"
                                    return@Button
                                }

                                if (password.length < 6) {
                                    errorMessage = "Password must be at least 6 characters"
                                    return@Button
                                }

                                isLoading = true
                                errorMessage = ""

                                // Create user with email and password
                                auth.createUserWithEmailAndPassword(username, password)
                                    .addOnCompleteListener { task ->
                                        isLoading = false
                                        if (task.isSuccessful) {
                                            // User created successfully, now store additional info
                                            val user = auth.currentUser
                                            val userInfo = hashMapOf(
                                                "email" to username,
                                                "createdAt" to System.currentTimeMillis()
                                            )

                                            // Add user to Firestore
                                            db.collection("users")
                                                .document(user?.uid ?: "")
                                                .set(userInfo)
                                                .addOnSuccessListener {
                                                    // Navigate to success screen
                                                    navController.navigate("registration_success")
                                                }
                                                .addOnFailureListener { e ->
                                                    errorMessage = "Failed to save user data: ${e.message}"
                                                }
                                        } else {
                                            // If sign up fails, display a message to the user
                                            errorMessage = "Registration failed: ${task.exception?.message ?: "Unknown error"}"
                                        }
                                    }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Sign Up",
                                    color = Color.Black,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Already have an account text
                        Text(
                            text = "Already have an account? Sign in.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RegistrationSuccessScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD5E8FF)) // Light blue background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with logo
            LittleCartsLogo()
            Spacer(modifier = Modifier.height(72.dp))

            // Success Card
            Card(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .padding(32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Account Registered!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sign In button
                    Button(
                        onClick = { navController.navigate("sign_in") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFFBE6)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Sign In",
                            color = Color.Black,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tip text
                    Text(
                        text = "Tips: Time is money so make all things efficient",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(navController: NavController, auth: FirebaseAuth) {
    // Direct state variables like in the third code
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD5E8FF)) // Light blue background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with logo
            LittleCartsLogo()
            Spacer(modifier = Modifier.height(16.dp))

            // Sign In Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFFFFBE6)) // Cream background
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { navController.navigate("sign_up") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF8E3FF)
                                ),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .height(40.dp)
                            ) {
                                Text(
                                    text = "Sign Up",
                                    color = Color.Black,
                                    fontSize = 14.sp
                                )
                            }

                            Text(
                                text = "Sign\nIn",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                textAlign = TextAlign.End
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Welcome back!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Email field
                        Text(
                            text = "Email",
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                containerColor = Color.White
                            )
                        )

                        // Password field
                        Text(
                            text = "Password",
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                containerColor = Color.White
                            ),
                            visualTransformation = PasswordVisualTransformation()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Display error message if any
                        if (errorMessage.isNotEmpty()) {
                            Text(
                                text = errorMessage,
                                color = Color.Red,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Sign In button with Firebase integration
                        Button(
                            onClick = {
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "Please fill in all fields"
                                    return@Button
                                }

                                isLoading = true
                                errorMessage = ""

                                // Navigate to loading screen first
                                navController.navigate("signing_in")

                                // Sign in with email and password
                                auth.signInWithEmailAndPassword(email, password)
                                    .addOnCompleteListener { task ->
                                        isLoading = false
                                        if (task.isSuccessful) {
                                            // Sign in success, navigate to homepage
                                            navController.navigate("homepage") {
                                                popUpTo("sign_in") { inclusive = true }
                                            }
                                        } else {
                                            // If sign in fails, go back and display error
                                            navController.popBackStack()
                                            errorMessage = "Authentication failed: ${task.exception?.message ?: "Invalid credentials"}"
                                        }
                                    }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Sign In",
                                    color = Color.Black,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Don't have an account text
                        Text(
                            text = "Don't have an account? Sign Up.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SigningInScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD5E8FF)) // Light blue background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with logo
            LittleCartsLogo()
            Spacer(modifier = Modifier.height(72.dp))

            // Signing In Card
            Card(
                modifier = Modifier
                    .padding(32.dp)
                    .clip(RoundedCornerShape(32.dp))
            ) {
                Column(
                    modifier = Modifier
                        .background(Color(0xFFF8E3FF)) // Light purple background
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Signing in ...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Daily Tip: Every second in your life is priceless",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Please wait a second...",
                fontSize = 16.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Loading indicator
            LinearProgressIndicator(
                modifier = Modifier
                    .width(200.dp)
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun LittleCartsLogo() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFFBE6))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "LITTLE",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Icon(
                Icons.Filled.ShoppingCart,
                contentDescription = "Cart Logo",
                tint = Color(0xFFD2691E),
                modifier = Modifier
                    .size(32.dp)
                    .padding(horizontal = 4.dp)
            )
            Text(
                text = "CARTS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

@Composable
fun LittleCartsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFC75D5D),
            background = Color(0xFFFFFBE6)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun UploadItemScreen(navController: NavController) {
    // State variables for user input
    var itemName by remember { mutableStateOf("") }
    var itemDescription by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var itemCategory by remember { mutableStateOf("") }
    var itemStock by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // Firebase instances
    val auth = Firebase.auth
    val db = Firebase.firestore
    val context = LocalContext.current

    // Category options
    val categories = listOf("Snacks", "Beverages", "Essential Goods", "Electronics", "Clothing", "Books")
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Text(
                "Upload New Item",
                color = Color.Black,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp)) // Balance the back button
        }

        Spacer(Modifier.height(24.dp))

        // Item Name
        OutlinedTextField(
            value = itemName,
            onValueChange = { itemName = it },
            label = { Text("Item Name") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = Color(0xFF2196F3),
                unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Item Description
        OutlinedTextField(
            value = itemDescription,
            onValueChange = { itemDescription = it },
            label = { Text("Item Description") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = Color(0xFF2196F3),
                unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(Modifier.height(12.dp))

        // Price
        OutlinedTextField(
            value = itemPrice,
            onValueChange = { itemPrice = it },
            label = { Text("Price") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = Color(0xFF2196F3),
                unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Category Dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = itemCategory,
                onValueChange = { },
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color.Gray
                ),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            itemCategory = category
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Stock
        OutlinedTextField(
            value = itemStock,
            onValueChange = { itemStock = it },
            label = { Text("Stock Quantity") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = Color(0xFF2196F3),
                unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Image URL
        OutlinedTextField(
            value = imageUrl,
            onValueChange = { imageUrl = it },
            label = { Text("Image URL") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = Color(0xFF2196F3),
                unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://example.com/image.jpg") }
        )

        Spacer(Modifier.height(32.dp))

        // Submit Button
        Button(
            onClick = {
                if (itemName.isNotBlank() && itemDescription.isNotBlank() &&
                    itemPrice.isNotBlank() && itemCategory.isNotBlank() && itemStock.isNotBlank()) {
                    isSubmitting = true

                    // Create a unique ID for this product
                    val itemId = db.collection("product").document().id

                    // Convert price and stock to appropriate types
                    val priceValue = itemPrice.toDoubleOrNull() ?: 0.0
                    val stockValue = itemStock.toIntOrNull() ?: 0

                    // Create the product object as a HashMap
                    val productItem = hashMapOf(
                        "id" to itemId,
                        "name" to itemName,
                        "description" to itemDescription,
                        "price" to priceValue,
                        "category" to itemCategory,
                        "stock" to stockValue,
                        "imageUrl" to imageUrl,
                        "isActive" to true,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )

                    // Save to Firestore
                    db.collection("product").document(itemId)
                        .set(productItem)
                        .addOnSuccessListener {
                            isSubmitting = false
                            Toast.makeText(context, "Item uploaded successfully!", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                        .addOnFailureListener { exception ->
                            isSubmitting = false
                            Toast.makeText(context, "Failed to upload item: ${exception.message}", Toast.LENGTH_LONG).show()
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(Color(0xFF2196F3)),
            enabled = !isSubmitting && itemName.isNotBlank() && itemDescription.isNotBlank() &&
                    itemPrice.isNotBlank() && itemCategory.isNotBlank() && itemStock.isNotBlank()
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text("Upload Item", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProductCard(
    item: ProductItem,
    userId: String,
    cartRepository: CartRepository = CartRepository() // Add CartRepository parameter
) {
    var isLoading by remember { mutableStateOf(false) } // Track loading state
    var showSuccess by remember { mutableStateOf(false) } // Track success state

    // Reset success state after showing it
    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(1000) // Show success for 1 second
            showSuccess = false
        }
    }

    Card(
        modifier = Modifier
            .width(150.dp)
            .height(180.dp)
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = LittleCartsPink),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Product image - AsyncImage or placeholder
            if (item.imageUrl.isNotEmpty()) {
                // Use AsyncImage from Coil library for loading images
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(90.dp)
                        .background(Color(0xFFE6E6FA), RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(Color(0xFFE6E6FA), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.name.take(2).uppercase(),
                        fontSize = 14.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = item.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Rp. ${String.format("%.0f", item.price)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.DarkGray
            )

            Button(
                onClick = {
                    if (!isLoading) {
                        isLoading = true
                        cartRepository.addToCart(
                            userId = userId,
                            productItem = item,
                            quantity = 1,
                            onSuccess = {
                                isLoading = false
                                showSuccess = true
                                Log.d("ProductCard", "Item added to cart: ${item.name}")
                            },
                            onFailure = { exception ->
                                isLoading = false
                                Log.e("ProductCard", "Failed to add item to cart", exception)
                                // You might want to show a toast or snackbar here
                            }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        showSuccess -> Color.Green
                        isLoading -> Color.Gray
                        else -> Color(0xFF4169E1)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(4.dp),
                enabled = !isLoading
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                    showSuccess -> {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Added",
                                modifier = Modifier.size(12.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ADDED",
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "ADD TO CART",
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductSection(
    title: String,
    items: List<ProductItem>,
    cartRepository: CartRepository = CartRepository() // Add CartRepository parameter
) {
    if (items.isNotEmpty()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    ProductCard(
                        item = item,
                        userId = getCurrentUserId(),
                        cartRepository = cartRepository // Pass CartRepository
                    )
                }
            }
        }
    }
}


@Composable
fun HomepageScreen(navController: NavController) {
    val scrollState = rememberScrollState()
    var searchQuery by remember { mutableStateOf("") }

    // Firebase Firestore instance
    val db = Firebase.firestore

    // State for products
    var products by remember { mutableStateOf<List<ProductItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fetch products from Firebase
    LaunchedEffect(Unit) {
        db.collection("product")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val productList = documents.documents.mapNotNull { doc ->
                    doc.data?.let { data ->
                        try {
                            ProductItem(
                                id = doc.id,
                                name = data["name"] as? String ?: "",
                                description = data["description"] as? String ?: "",
                                price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                                category = data["category"] as? String ?: "",
                                stock = (data["stock"] as? Number)?.toInt() ?: 0,
                                imageUrl = data["imageUrl"] as? String ?: "",
                                isActive = data["isActive"] as? Boolean ?: true,
                                createdAt = data["createdAt"] as? com.google.firebase.Timestamp
                            )
                        } catch (e: Exception) {
                            Log.e("HomepageScreen", "Error parsing product: ${e.message}")
                            null
                        }
                    }
                }
                products = productList
                isLoading = false
            }
            .addOnFailureListener { exception ->
                Log.e("HomepageScreen", "Error getting products: ", exception)
                errorMessage = "Failed to load products: ${exception.message}"
                isLoading = false
            }
    }

    // Filter products by category
    val snackItems = products.filter { it.category.equals("Snacks", ignoreCase = true) }
    val beverageItems = products.filter { it.category.equals("Beverages", ignoreCase = true) }
    val essentialItems = products.filter { it.category.equals("Essential Goods", ignoreCase = true) }

    // Filter products based on search query
    val filteredProducts = if (searchQuery.isBlank()) {
        products
    } else {
        products.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD5E8FF)) // Light blue background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            LittleCartsLogo()

            // Navigation Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFFF6CEE7), RoundedCornerShape(20.dp))
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = "HOME",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable { }
                    )
                    Text(
                        text = "ABOUT US",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.Black,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable { navController.navigate("about_us") }
                    )
                    Text(
                        text = "PROFILE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.Black,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable { navController.navigate("profile") }
                    )
                }
            }

            // Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search", fontSize = 14.sp) },
                    trailingIcon = {
                        IconButton(onClick = { /* Search action */ }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF6A5ACD),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF6A5ACD)
                    ),
                    singleLine = true
                )
            }

            // Content based on loading state
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF6A5ACD))
                    }
                }

                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Error loading products",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Red
                            )
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Button(
                                onClick = {
                                    // Retry logic
                                    isLoading = true
                                    errorMessage = null
                                    // Re-trigger LaunchedEffect by changing the key
                                },
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                searchQuery.isNotBlank() -> {
                    // Show search results
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        if (filteredProducts.isNotEmpty()) {
                            Text(
                                text = "Search Results (${filteredProducts.size})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredProducts) { item ->
                                    ProductCard(item = item, userId = getCurrentUserId())
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No products found for \"$searchQuery\"",
                                    fontSize = 16.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }

                else -> {
                    // Show products by category
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        ProductSection(title = "Snacks", items = snackItems)
                        ProductSection(title = "Beverages", items = beverageItems)
                        ProductSection(title = "Essential Goods", items = essentialItems)
                        Spacer(modifier = Modifier.height(80.dp)) // Space for bottom cart
                    }
                }
            }
        }

        // Di hide karena hanya untuk upload barang
//        FloatingActionButton(
//            onClick = { navController.navigate("upload_item") },
//            modifier = Modifier
//                .align(Alignment.BottomStart)
//                .padding(16.dp),
//            containerColor = Color(0xFFE6A8E6),
//            contentColor = Color.Black
//        ) {
//            Icon(
//                Icons.Filled.Add,
//                contentDescription = "Upload Item",
//                tint = Color.Black
//            )
//        }

        // Button Shopping Cart
        FloatingActionButton(
            onClick = { navController.navigate("shopping_cart") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF4169E1)
        ) {
            Icon(
                Icons.Filled.ShoppingCart,
                contentDescription = "Shopping Cart",
                tint = Color.White
            )
        }
    }
}

@Composable
fun AboutUsScreen(navController: NavController) {
    // Use a Column to stack your UI elements vertically
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD5E8FF))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LittleCartsLogo()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xFFF6CEE7), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "HOME",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.Black,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable { navController.navigate("homepage") }
                )
                Text(
                    text = "ABOUT US",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable { }
                )
                Text(
                    text = "PROFILE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.Black,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable { navController.navigate("profile") }
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ABOUT US",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(vertical = 20.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 300.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LemonChiffon)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Selamat datang di Little Carts! Aplikasi ini adalah contoh untuk mendemonstrasikan kemampuan UI dengan Jetpack Compose. Kami berfokus pada penyediaan antarmuka pengguna yang bersih, menarik, dan mudah digunakan.\n\nDi sini Anda dapat menjelajahi berbagai fitur yang telah kami rancang. Misi kami adalah membuat pengalaman berbelanja Anda menjadi lebih menyenangkan dan efisien.\n\nTerima kasih telah menggunakan Little Carts!",
                        fontSize = 16.sp,
                        color = LittleCartsTextColor,
                        textAlign = TextAlign.Justify
                    )
                }
            }
        }
    }
}

// Save Profile Picture via local storage, lebih mudah untuk dimodif
fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val fileName = "profile_image.jpg"
        val file = File(context.filesDir, fileName)
        val outputStream = FileOutputStream(file)

        inputStream?.copyTo(outputStream)

        inputStream?.close()
        outputStream.close()

        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun ProfileScreen(
    navController: NavController,
    userId: String,
    orderRepository: OrderRepository = OrderRepository()
) {

    // Untuk profile picture
    val context = LocalContext.current
    val sharedPref = remember {
        context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    }
    val savedPath = sharedPref.getString("profileImagePath", null)
    var imageFilePath by remember { mutableStateOf(savedPath) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { it ->
            val path = saveImageToInternalStorage(context, it)
            path?.let {
                imageFilePath = it
                sharedPref.edit().putString("profileImagePath", it).apply()
            }
        }
    }

    // User data states
    var username by remember { mutableStateOf(sharedPref.getString("username", "user123") ?: "user123") }
    var notificationsEnabled by remember { mutableStateOf(sharedPref.getBoolean("notifications", true)) }

    // Username editing states
    var showUsernameDialog by remember { mutableStateOf(false) }
    var tempUsername by remember { mutableStateOf("") }

    // Function to save username
    fun saveUsername(newUsername: String) {
        sharedPref.edit().putString("username", newUsername).apply()
        username = newUsername
    }

    // Simplified transaction history states
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    // Load orders - simplified
    LaunchedEffect(userId) {
        try {
            orderRepository.getUserOrders(
                userId = userId,
                onSuccess = { orderList ->
                    orders = orderList.take(5) // Limit to 5 orders to keep it simple
                    isLoading = false
                    hasError = false
                },
                onFailure = {
                    hasError = true
                    isLoading = false
                }
            )
        } catch (e: Exception) {
            hasError = true
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD5E8FF))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        LittleCartsLogo()

        // Navigation Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xFFF6CEE7), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "HOME",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.Black,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable { navController.navigate("homepage") }
                )
                Text(
                    text = "ABOUT US",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.Black,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable { navController.navigate("about_us") }
                )
                Text(
                    text = "PROFILE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Profile Picture Section
        Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.fillMaxWidth()) {
            if (imageFilePath != null && File(imageFilePath!!).exists()) {
                Image(
                    painter = rememberAsyncImagePainter(File(imageFilePath!!)),
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .align(Alignment.Center)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Default Profile",
                    tint = Color.White,
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.Center)
                )
            }
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Profile Picture",
                tint = Color(0xFFF6CEE7),
                modifier = Modifier
                    .offset(x = (-32).dp, y = (-8).dp)
                    .size(24.dp)
                    .clickable { launcher.launch("image/*") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Username Section with Edit Dialog
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Username",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Username",
                        tint = Color(0xFFE6A8E6),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                tempUsername = username
                                showUsernameDialog = true
                            }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hi, $username!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF666666)
                )
            }
        }

        // Notifications Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Allow Notifications",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = {
                        notificationsEnabled = it
                        sharedPref.edit().putBoolean("notifications", it).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFE6A8E6),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray
                    )
                )
            }
        }

        // Simplified Transaction History Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Recent Orders",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFE6A8E6),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    hasError -> {
                        Text(
                            text = "Unable to load orders",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    orders.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "No Orders",
                                tint = Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No orders yet",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    else -> {
                        orders.forEach { order ->
                            SimpleOrderItem(order = order)
                            if (order != orders.last()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Username Dialog
    if (showUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            title = {
                Text(
                    text = "Edit Username",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your new username:",
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = tempUsername,
                        onValueChange = { tempUsername = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE6A8E6),
                            cursorColor = Color(0xFFE6A8E6)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempUsername.isNotBlank()) {
                            saveUsername(tempUsername.trim())
                        }
                        showUsernameDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFE6A8E6)
                    )
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUsernameDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF666666)
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun SimpleOrderItem(order: Order) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F9FA), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Simple order icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFFE6A8E6), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = "Order",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${order.items.size} items",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Text(
                text = try {
                    dateFormat.format(order.orderDate)
                } catch (e: Exception) {
                    "Recent order"
                },
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Text(
            text = "Rp.${String.format("%.0f", order.totalAmount)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333)
        )
    }
}

// Cart Repository for Firebase operations
class CartRepository {
    private val db = Firebase.firestore

    // Add item to cart
    fun addToCart(
        userId: String,
        productItem: ProductItem,
        quantity: Int = 1,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val cartRef = db.collection("carts").document(userId)

        cartRef.get()
            .addOnSuccessListener { document ->
                val currentItems = if (document.exists()) {
                    // Get existing items
                    val itemsArray = document.get("items") as? List<Map<String, Any>> ?: emptyList()
                    itemsArray.map { itemMap ->
                        CartItem(
                            productId = itemMap["productId"] as? String ?: "",
                            name = itemMap["name"] as? String ?: "",
                            price = (itemMap["price"] as? Number)?.toDouble() ?: 0.0,
                            quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 1,
                            imageUrl = itemMap["imageUrl"] as? String ?: ""
                        )
                    }.toMutableList()
                } else {
                    mutableListOf()
                }

                // Check if item already exists
                val existingItemIndex = currentItems.indexOfFirst { it.productId == productItem.id }

                if (existingItemIndex != -1) {
                    // Update existing item quantity
                    currentItems[existingItemIndex] = currentItems[existingItemIndex].copy(
                        quantity = currentItems[existingItemIndex].quantity + quantity
                    )
                } else {
                    // Add new item
                    currentItems.add(
                        CartItem(
                            productId = productItem.id,
                            name = productItem.name,
                            price = productItem.price,
                            quantity = quantity,
                            imageUrl = productItem.imageUrl
                        )
                    )
                }

                // Calculate total price
                val totalPrice = currentItems.sumOf { it.price * it.quantity }

                // Convert to maps for Firestore
                val itemMaps = currentItems.map { item ->
                    mapOf(
                        "productId" to item.productId,
                        "name" to item.name,
                        "price" to item.price,
                        "quantity" to item.quantity,
                        "imageUrl" to item.imageUrl
                    )
                }

                // Update cart document
                val cartData = mapOf(
                    "userId" to userId,
                    "items" to itemMaps,
                    "totalPrice" to totalPrice
                )

                cartRef.set(cartData)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onFailure(it) }
            }
            .addOnFailureListener { onFailure(it) }
    }

    // Get cart items with real-time listener
    fun getCartItems(
        userId: String,
        onSuccess: (List<CartItem>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("carts")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onFailure(error)
                    return@addSnapshotListener
                }

                if (snapshot?.exists() == true) {
                    val itemsArray = snapshot.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val cartItems = itemsArray.map { itemMap ->
                        CartItem(
                            productId = itemMap["productId"] as? String ?: "",
                            name = itemMap["name"] as? String ?: "",
                            price = (itemMap["price"] as? Number)?.toDouble() ?: 0.0,
                            quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 1,
                            imageUrl = itemMap["imageUrl"] as? String ?: ""
                        )
                    }
                    onSuccess(cartItems)
                } else {
                    onSuccess(emptyList())
                }
            }
    }

    // Update item quantity
    fun updateCartItemQuantity(
        userId: String,
        productId: String,
        newQuantity: Int,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val cartRef = db.collection("carts").document(userId)

        cartRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val itemsArray = document.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val updatedItems = itemsArray.map { itemMap ->
                        if (itemMap["productId"] == productId) {
                            itemMap.toMutableMap().apply {
                                this["quantity"] = newQuantity
                            }
                        } else {
                            itemMap
                        }
                    }

                    // Calculate new total price
                    val totalPrice = updatedItems.sumOf { itemMap ->
                        val price = (itemMap["price"] as? Number)?.toDouble() ?: 0.0
                        val quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 0
                        price * quantity
                    }

                    cartRef.update(
                        mapOf(
                            "items" to updatedItems,
                            "totalPrice" to totalPrice
                        )
                    )
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onFailure(it) }
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    // Remove item from cart
    fun removeCartItem(
        userId: String,
        productId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val cartRef = db.collection("carts").document(userId)

        cartRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val itemsArray = document.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val filteredItems = itemsArray.filter { itemMap ->
                        itemMap["productId"] != productId
                    }

                    // Calculate new total price
                    val totalPrice = filteredItems.sumOf { itemMap ->
                        val price = (itemMap["price"] as? Number)?.toDouble() ?: 0.0
                        val quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 0
                        price * quantity
                    }

                    if (filteredItems.isEmpty()) {
                        // Delete the entire cart document if no items left
                        cartRef.delete()
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onFailure(it) }
                    } else {
                        cartRef.update(
                            mapOf(
                                "items" to filteredItems,
                                "totalPrice" to totalPrice
                            )
                        )
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onFailure(it) }
                    }
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    // Clear entire cart (useful after successful checkout)
    fun clearCart(
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("carts")
            .document(userId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    // Get cart total (alternative method if you want to get total without items)
    fun getCartTotal(
        userId: String,
        onSuccess: (Double) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("carts")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val totalPrice = if (document.exists()) {
                    (document.get("totalPrice") as? Number)?.toDouble() ?: 0.0
                } else {
                    0.0
                }
                onSuccess(totalPrice)
            }
            .addOnFailureListener { onFailure(it) }
    }

    // Get cart items count
    fun getCartItemsCount(
        userId: String,
        onSuccess: (Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("carts")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val count = if (document.exists()) {
                    val itemsArray = document.get("items") as? List<Map<String, Any>> ?: emptyList()
                    itemsArray.sumOf { itemMap ->
                        (itemMap["quantity"] as? Number)?.toInt() ?: 0
                    }
                } else {
                    0
                }
                onSuccess(count)
            }
            .addOnFailureListener { onFailure(it) }
    }
}

fun getCurrentUserId(): String {
    return Firebase.auth.currentUser?.uid ?: "anonymous_user"
}

@Composable
fun ShoppingCartScreen(
    navController: NavController,
    userId: String, // Pass this from your auth system
    cartRepository: CartRepository = CartRepository(),
    onCheckout: (List<CartItem>, Double) -> Unit
) {
    var cartItems by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val subtotal = cartItems.sumOf { it.price * it.quantity }
    val deliveryFee = 10000.0
    val tax = 1000.0
    val total = subtotal + deliveryFee + tax

    // Load cart items from Firebase
    LaunchedEffect(userId) {
        cartRepository.getCartItems(
            userId = userId,
            onSuccess = { items ->
                cartItems = items
                isLoading = false
                errorMessage = null
            },
            onFailure = { exception ->
                errorMessage = "Failed to load cart: ${exception.message}"
                isLoading = false
                Log.e("ShoppingCart", "Error loading cart", exception)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD5E8FF))
    ) {
        LittleCartsLogo()

        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFD5E8FF))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black
                )
            }
            Text(
                text = "Shopping Cart (${cartItems.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF6A5ACD))
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Error loading cart",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                        Text(
                            text = errorMessage ?: "",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            cartItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Empty Cart",
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Text(
                            text = "Your cart is empty",
                            color = Color.Gray,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Button(
                            onClick = { navController.navigate("homepage") },
                            modifier = Modifier.padding(top = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4169E1)
                            )
                        ) {
                            Text("Continue Shopping", color = Color.White)
                        }
                    }
                }
            }

            else -> {
                // Cart items list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(cartItems, key = { it.productId }) { item ->
                        CartItemCard(
                            item = item,
                            onQuantityChange = { qty ->
                                if (qty > 0) {
                                    cartRepository.updateCartItemQuantity(
                                        userId = userId,
                                        productId = item.productId,
                                        newQuantity = qty,
                                        onSuccess = {
                                            // Update handled by real-time listener
                                        },
                                        onFailure = { exception ->
                                            Log.e("ShoppingCart", "Error updating quantity", exception)
                                        }
                                    )
                                } else {
                                    cartRepository.removeCartItem(
                                        userId = userId,
                                        productId = item.productId,
                                        onSuccess = {
                                            // Update handled by real-time listener
                                        },
                                        onFailure = { exception ->
                                            Log.e("ShoppingCart", "Error removing item", exception)
                                        }
                                    )
                                }
                            },
                            onRemove = {
                                cartRepository.removeCartItem(
                                    userId = userId,
                                    productId = item.productId,
                                    onSuccess = {
                                        // Update handled by real-time listener
                                    },
                                    onFailure = { exception ->
                                        Log.e("ShoppingCart", "Error removing item", exception)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        // Total and checkout button
        if (cartItems.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Column {
                    PaymentRow("Subtotal", subtotal)
                    PaymentRow("Delivery Fee", deliveryFee)
                    PaymentRow("Tax", tax)

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color.Gray.copy(alpha = 0.3f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "Rp.${String.format("%.0f", total)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onCheckout(cartItems, total)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE6A8E6)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Checkout (${cartItems.size})",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(label: String, amount: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = "Rp.${String.format("%.0f", amount)}",
            fontSize = 14.sp,
            color = Color.Black
        )
    }
}

@Composable
private fun CartItemCard(
    item: CartItem,
    onQuantityChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    var isUpdating by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color(0xFFE6A8E6),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Product image
            if (item.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.Gray, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.Gray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.name.take(2).uppercase(),
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Product details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Rp.${String.format("%.0f", item.price)}",
                    fontSize = 14.sp,
                    color = Color.Red,
                    fontWeight = FontWeight.Medium
                )
            }

            // Quantity controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        if (!isUpdating) {
                            isUpdating = true
                            onQuantityChange(item.quantity - 1)
                            // Reset updating state after a delay
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(500)
                                isUpdating = false
                            }
                        }
                    },
                    modifier = Modifier.size(32.dp),
                    enabled = !isUpdating && item.quantity > 1
                ) {
                    Text(
                        text = "-",
                        fontSize = 18.sp,
                        color = if (item.quantity > 1) Color.Black else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = item.quantity.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    modifier = Modifier.widthIn(min = 24.dp),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = {
                        if (!isUpdating) {
                            isUpdating = true
                            onQuantityChange(item.quantity + 1)
                            // Reset updating state after a delay
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(500)
                                isUpdating = false
                            }
                        }
                    },
                    modifier = Modifier.size(32.dp),
                    enabled = !isUpdating
                ) {
                    Text(
                        text = "+",
                        fontSize = 18.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


// Simple OrderRepository - just save and get
class OrderRepository {
    private val db = FirebaseFirestore.getInstance()
    private val ordersCollection = db.collection("orders")

    // Save order - simple one function
    fun saveOrder(
        userId: String,
        totalAmount: Double,
        cartItems: List<CartItem>,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val orderId = ordersCollection.document().id

        val orderItems = cartItems.map { cartItem ->
            OrderItem(
                productId = cartItem.productId,
                name = cartItem.name,
                priceAtPurchase = cartItem.price,
                quantity = cartItem.quantity,
                imageUrl = cartItem.imageUrl
            )
        }

        val order = Order(
            orderId = orderId,
            userId = userId,
            totalAmount = totalAmount,
            shippingAddress = "Default Address", // You can make this dynamic later
            items = orderItems
        )

        ordersCollection.document(orderId)
            .set(order)
            .addOnSuccessListener { onSuccess(orderId) }
            .addOnFailureListener { onFailure(it) }
    }

    // Get user orders - for transaction history later
    fun getUserOrders(
        userId: String,
        onSuccess: (List<Order>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        ordersCollection
            .get()
            .addOnSuccessListener { documents ->
                val orders = documents.map { it.toObject(Order::class.java) }
                onSuccess(orders)
            }
            .addOnFailureListener { onFailure(it) }
    }
}

@Composable
fun CheckOutScreen(
    navController: NavController,
    totalAmount: Double,
    itemCount: Int,
    userId: String,
    cartItems: List<CartItem>,
    orderRepository: OrderRepository = OrderRepository()
) {
    var orderId by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(true) }
    var showSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Save order when screen loads
    LaunchedEffect(Unit) {
        orderRepository.saveOrder(
            userId = userId,
            totalAmount = totalAmount,
            cartItems = cartItems,
            onSuccess = { generatedOrderId ->
                orderId = generatedOrderId
                isProcessing = false
                showSuccess = true
            },
            onFailure = { exception ->
                isProcessing = false
                errorMessage = "Failed to save order: ${exception.message}"
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD5E8FF)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LittleCartsLogo()

        // Back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            IconButton(onClick = { navController.navigate("homepage") }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        when {
            isProcessing -> {
                CircularProgressIndicator(color = Color(0xFF6A5ACD))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Processing order...", color = Color.Gray)
            }

            errorMessage != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Order Failed", fontSize = 18.sp, color = Color.Red)
                    Text(errorMessage ?: "", fontSize = 14.sp, color = Color.Gray)
                    Button(onClick = { navController.navigate("homepage") }) {
                        Text("Back to Home")
                    }
                }
            }

            showSuccess -> {
                // Success icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(Color.Green, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Rp.${String.format("%.0f", totalAmount)}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Text(
                    text = "ORDER PLACED SUCCESSFULLY",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Green,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Order ID", fontSize = 14.sp, color = Color.Gray)
                    Text(
                        text = orderId,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Date & Time", fontSize = 14.sp, color = Color.Gray)
                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                            .format(Date()),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { navController.navigate("homepage") },
            modifier = Modifier
                .padding(16.dp)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = "BACK TO HOME",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}