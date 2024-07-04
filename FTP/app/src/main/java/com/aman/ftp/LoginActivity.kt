package com.aman.ftp

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aman.ftp.ui.theme.MyApplicationTheme
import com.google.firebase.database.*

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false)

        if (isLoggedIn) {
            // User is already logged in, navigate to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            setContent {
                MyApplicationTheme {
                    LoginScreen()
                }
            }
        }
    }
}

@Composable
fun LoginScreen() {
    var usernameOrEmailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val context = LocalContext.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.login),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Card(
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = usernameOrEmailOrPhone,
                    onValueChange = { usernameOrEmailOrPhone = it },
                    label = { Text("Username or Email ID or Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Button(
                    onClick = {
                        if (usernameOrEmailOrPhone.isEmpty() || password.isEmpty()) {
                            Toast.makeText(context, "Fields are empty", Toast.LENGTH_SHORT).show()
                        } else {
                            handleLogin(usernameOrEmailOrPhone, password, context)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text("Login")
                }
                ClickableText(
                    text = AnnotatedString("Sign Up"),
                    onClick = {
                        context.startActivity(Intent(context, SignUpActivity::class.java))
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

fun handleLogin(usernameOrEmailOrPhone: String, password: String, context: android.content.Context) {
    val database = FirebaseDatabase.getInstance().reference.child("users")
    val queries = listOf(
        database.orderByChild("username").equalTo(usernameOrEmailOrPhone),
        database.orderByChild("email").equalTo(usernameOrEmailOrPhone),
        database.orderByChild("mobileNumber").equalTo(usernameOrEmailOrPhone)
    )

    queries.forEach { query ->
        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    loginUser(snapshot, password, context)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Database error occurred", Toast.LENGTH_SHORT).show()
                Log.e("LoginActivity", "Database error: ${error.message}")
            }
        })
    }
}

fun loginUser(snapshot: DataSnapshot, password: String, context: android.content.Context) {
    for (userSnapshot in snapshot.children) {
        val user = userSnapshot.getValue(User::class.java)
        if (user != null && user.password == password) {
            Toast.makeText(context, "Login Successfully", Toast.LENGTH_SHORT).show()

            val sharedPreferences = context.getSharedPreferences("LoginPrefs", MODE_PRIVATE)
            val editor = sharedPreferences.edit()

            editor.putBoolean("isLoggedIn", true)
            editor.apply()

            val intent = Intent(context, MainActivity::class.java)
            context.startActivity(intent)
            (context as ComponentActivity).finish()
            return
        }
    }
    Toast.makeText(context, "Username or Password is incorrect", Toast.LENGTH_SHORT).show()
}
