package com.aman.ftp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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

class SignUpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                SignUpScreen()
            }
        }
    }
}

@Composable
fun SignUpScreen() {
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val context = LocalContext.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.sign_up),
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
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email ID") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = mobileNumber,
                    onValueChange = {
                        if (it.length <= 10) mobileNumber = it
                    },
                    label = { Text("Mobile Number") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Button(
                    onClick = {
                        if (username.isEmpty() || fullName.isEmpty() || email.isEmpty() || mobileNumber.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                            Toast.makeText(context, "Please fill all the fields", Toast.LENGTH_SHORT).show()
                        } else if (mobileNumber.length != 10) {
                            Toast.makeText(context, "Please enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                        } else if (password != confirmPassword) {
                            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                        } else {
                            val database = FirebaseDatabase.getInstance().reference.child("users")
                            val query = database.orderByChild("username").equalTo(username)

                            query.addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        Toast.makeText(context, "Username already exists", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val emailQuery = database.orderByChild("email").equalTo(email)
                                        emailQuery.addListenerForSingleValueEvent(object : ValueEventListener {
                                            override fun onDataChange(emailSnapshot: DataSnapshot) {
                                                if (emailSnapshot.exists()) {
                                                    Toast.makeText(context, "Email already registered", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val phoneQuery = database.orderByChild("mobileNumber").equalTo(mobileNumber)
                                                    phoneQuery.addListenerForSingleValueEvent(object : ValueEventListener {
                                                        override fun onDataChange(phoneSnapshot: DataSnapshot) {
                                                            if (phoneSnapshot.exists()) {
                                                                Toast.makeText(context, "Mobile number already registered", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                val user = User(username, fullName, email, mobileNumber, password)
                                                                val newUserRef = database.push()
                                                                newUserRef.setValue(user)
                                                                    .addOnSuccessListener {
                                                                        Toast.makeText(context, "Sign Up Successful", Toast.LENGTH_SHORT).show()
                                                                        context.startActivity(Intent(context, LoginActivity::class.java))
                                                                        (context as ComponentActivity).finish()
                                                                    }
                                                                    .addOnFailureListener { e ->
                                                                        Toast.makeText(context, "Sign Up Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                        Log.e("SignUpActivity", "Error: ${e.message}")
                                                                    }
                                                            }
                                                        }

                                                        override fun onCancelled(error: DatabaseError) {
                                                            Toast.makeText(context, "Database error occurred", Toast.LENGTH_SHORT).show()
                                                            Log.e("SignUpActivity", "Database error: ${error.message}")
                                                        }
                                                    })
                                                }
                                            }

                                            override fun onCancelled(error: DatabaseError) {
                                                Toast.makeText(context, "Database error occurred", Toast.LENGTH_SHORT).show()
                                                Log.e("SignUpActivity", "Database error: ${error.message}")
                                            }
                                        })
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Toast.makeText(context, "Database error occurred", Toast.LENGTH_SHORT).show()
                                    Log.e("SignUpActivity", "Database error: ${error.message}")
                                }
                            })
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text("Sign Up")
                }
                ClickableText(
                    text = AnnotatedString("Login"),
                    onClick = {
                        context.startActivity(Intent(context, LoginActivity::class.java))
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
