package n.rnu.isetr.puzzlegame

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.WindowManager

class Welcoming_Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcoming)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )


        Handler().postDelayed({
            val mainIntent = Intent(this@Welcoming_Activity, MainActivity::class.java)
            startActivity(mainIntent)
            finish()
        }, 4000)
    }


}