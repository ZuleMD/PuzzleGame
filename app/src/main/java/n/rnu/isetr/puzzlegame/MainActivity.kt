package n.rnu.isetr.puzzlegame

import Puzzle.Helpimpl.*
import Puzzle.Helpimpl.SpinnerAdapter
import Puzzle.Helpimpl.Timer
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.karumi.dexter.listener.single.PermissionListener
import java.util.*
import java.util.Collections.swap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import android.content.Intent





class MainActivity : AppCompatActivity() {

    companion object {
        private const val NUM_COLUMNS = 3
        private const val NUM_TILES = NUM_COLUMNS * NUM_COLUMNS
        private const val BORDER_OFFSET = 6
        private const val BLANK_TILE_MARKER = NUM_TILES - 1
    }


    var image_uri: Uri? = null
    private lateinit var clRoot: ConstraintLayout
    private lateinit var gvgPuzzle: GridViewGesture
    private lateinit var btnCamera: Button
    private lateinit var btnShuffle: Button
    private lateinit var btnPlayAgain: Button
    private lateinit var tvMoveNumber: TextView
    private  lateinit var tvMove:TextView
    private lateinit var tvTimeTaken: TextView
    private lateinit var tvTime:TextView
    private lateinit var spnPuzzle: Spinner
    private lateinit var tvSuccess: TextView
    private lateinit var sp: SharedPreferences
    private var tileDimen: Int = 0
    private var puzzleDimen: Int = 0
    private var BestScore:Int=100
    private lateinit var goalPuzzleState: ArrayList<Int>
    private lateinit var puzzleState: ArrayList<Int>
    private var blankTilePos: Int = BLANK_TILE_MARKER
    private var isPuzzleGridFrozen: Boolean = false
    private var isGameInSession: Boolean = false
    private lateinit var puzzleImage: Bitmap
    private lateinit var imageChunks: ArrayList<Bitmap>
    private lateinit var blankImageChunks: ArrayList<Bitmap>
    private lateinit var tileImages: ArrayList<ImageButton>
    private lateinit var puzzleImageChoices: Array<PuzzleImages>
    private var puzzleImageIndex: Int = 0
    private var indexOfCustom: Int = 0
    private var isGalleryImageChosen: Boolean = false
    private lateinit var shuffleRunnable: ShuffleRunnable
    private lateinit var shuffleScheduler: ScheduledExecutorService
    private lateinit var shuffleHandler: Handler
    private lateinit var timerHandler: Handler
    private var isTimerRunning: Boolean = false
    private var numMoves: Long = 0
    private var timeTaken: Long = 0
    private val CAMERA_REQUEST_CODE = 1
    private val GALLERY_REQUEST_CODE = 2


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Hides the app bar
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        initComponents()
        initSharedPreferences()
        initHandlers()
        initStateAndTileImages()
        initPuzzle()
    }

    private fun initComponents() {
        clRoot = findViewById(R.id.cl_root)
        gvgPuzzle = findViewById(R.id.gvg_puzzle)
        btnShuffle = findViewById(R.id.btn_shuffle)
        setBtnShuffleAction()
        btnPlayAgain=findViewById(R.id.btn_playagain)
        setBtnPlayAgainAction()
        btnCamera = findViewById(R.id.btncamera)
        setBtnCameraAction()
        tvMoveNumber = findViewById(R.id.tv_move_number)
        tvMove=findViewById(R.id.tv_move)
        tvTimeTaken = findViewById(R.id.tv_time_taken)
        tvTime=findViewById(R.id.tv_time)
        tvSuccess = findViewById(R.id.tv_success)
        tvSuccess.setOnClickListener {
            tvSuccess.visibility = View.GONE
        }

        spnPuzzle = findViewById(R.id.spn_puzzle)
        spnPuzzle.adapter = SpinnerAdapter(
            this,
            R.layout.spinner_item,
            resources.getStringArray(R.array.puzzle_images)
        )
        puzzleImageChoices = PuzzleImages.values()
        indexOfCustom = puzzleImageChoices.lastIndex
    }

    ///Retrieves the values stored using shared preferences
    private fun initSharedPreferences() {
        sp = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)

    }

    //Initializes the handlers related to shuffling the tiles and triggering the game timer
    private fun initHandlers() {
        shuffleScheduler = Executors.newScheduledThreadPool(NUM_TILES)
        shuffleHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                super.handleMessage(message)

                showTileAt(message.data.getInt(Key.KEY_TILE_POSITION.name))
                updateComponents()
            }
        }

        timerHandler = Handler(Looper.getMainLooper())
    }

    //Initializes the puzzle state and the images of the tiles in the grid
    private fun initStateAndTileImages() {
        goalPuzzleState = ArrayList(NUM_TILES)
        puzzleState = ArrayList(NUM_TILES)
        tileImages = ArrayList(NUM_TILES)

        for (tile in 0 until NUM_TILES) {
            goalPuzzleState.add(tile)
            puzzleState.add(tile)
            tileImages.add(ImageButton(this))
        }
    }

    //Initializes the dynamic components and listeners related to the puzzle grid
    private fun initPuzzle() {
        setTouchSlopThreshold()
        setOnFlingListener()
        setDimensions()
    }
    override fun onResume() {
        super.onResume()

        if (isGalleryImageChosen) {
            isGalleryImageChosen = false
        } else {
            spnPuzzle.setSelection(puzzleImageIndex)
        }
    }


    //Resets the puzzle state back to the original state (that is, the goal state)
    private fun resetState() {
        puzzleState = goalPuzzleState.toMutableList() as ArrayList<Int>
        blankTilePos = BLANK_TILE_MARKER
    }

    /************************************************************************/

    //Camera and Gallery
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            CAMERA_REQUEST_CODE-> if (resultCode == RESULT_OK) {

                loadPuzzle(image_uri)
            }
            GALLERY_REQUEST_CODE-> if (resultCode == RESULT_OK) {
                loadPuzzle(data?.data)
            }
        }
    }
    private fun setBtnCameraAction(){
        btnCamera.setOnClickListener {
            cameraCheckPermission()
        }
    }
    private fun galleryCheckPermission() {

        Dexter.withContext(this).withPermission(
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ).withListener(object : PermissionListener {
            override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                gallery()
            }

            override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                Toast.makeText(
                    this@MainActivity,
                    "You have denied the storage permission to select image",
                    Toast.LENGTH_SHORT
                ).show()
                showRotationalDialogForPermission()
            }

            override fun onPermissionRationaleShouldBeShown(
                p0: PermissionRequest?, p1: PermissionToken?) {
                showRotationalDialogForPermission()
            }
        }).onSameThread().check()
    }
    private fun gallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }
    private fun cameraCheckPermission() {

        Dexter.withContext(this)
            .withPermissions(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.CAMERA).withListener(

                object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        report?.let {

                            if (report.areAllPermissionsGranted()) {
                                camera()
                            }

                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: MutableList<PermissionRequest>?,
                        p1: PermissionToken?) {
                        showRotationalDialogForPermission()
                    }

                }
            ).onSameThread().check()
    }
    private fun showRotationalDialogForPermission() {
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions"
                    + "required for this feature. It can be enable under App settings!!!")

            .setPositiveButton("Go TO SETTINGS") { _, _ ->

                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)

                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }

            .setNegativeButton("CANCEL") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }
    private fun camera() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "New Picture")
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera")
        image_uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri)
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)

    }
    /************************************************************************/

    //Sets the item selection event listener for the spinner
    private fun setSpnPuzzleAction() {
        spnPuzzle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position != indexOfCustom) {
                    loadPuzzle(position)
                } else {
                    galleryCheckPermission()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    //Resets the statistics displayed (setting their values to 0) at the start of a game (that is immediately after the shuffling animation finishes).
    private fun resetDisplayedStats() {
        numMoves = 0
        tvMoveNumber.text = numMoves.toString()
        timeTaken = 0
        tvTimeTaken.text = Timer.displayTime(timeTaken)
    }
    //Sets the distance in pixels a touch can wander before it is registered as a fling gesture.
    private fun setTouchSlopThreshold() {
        gvgPuzzle.setTouchSlopThreshold(ViewConfiguration.get(this).scaledTouchSlop)
    }
    //Sets the listener for responding to detected fling gestures.
    private fun setOnFlingListener() {
        gvgPuzzle.setFlingListener(object : OnFlingListener {
            override fun onFling(direction: FlingDirection, position: Int) {
                moveTile(direction, position)
            }
        })
    }

    /************************************************************************/

    //Sets the dimensions of the puzzle grid and its individual tiles.
    private fun setDimensions() {
        gvgPuzzle.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                gvgPuzzle.viewTreeObserver.removeOnGlobalLayoutListener(this)

                puzzleDimen = gvgPuzzle.measuredWidth
                tileDimen = puzzleDimen / NUM_COLUMNS

                setSpnPuzzleAction()
                initPuzzleImage()
                initChunks()
                displayPuzzle()
            }
        })
    }

    //Puzzle Image and Grid
    private fun initPuzzleImage() {
        puzzleImageIndex = sp.getInt(Key.KEY_PUZZLE_IMAGE.name, 0)
        spnPuzzle.setSelection(puzzleImageIndex)

        puzzleImage = ImageUtil.resizeToSquareBitmap(
            ImageUtil.drawableToBitmap(
                this@MainActivity,
                puzzleImageChoices[puzzleImageIndex].drawableId
            ),
            puzzleDimen,
            puzzleDimen
        )
    }
    private fun initChunks() {
        imageChunks =
            ImageUtil.splitBitmap(
                puzzleImage,
                tileDimen - BORDER_OFFSET,
                NUM_TILES,
                NUM_COLUMNS
            ).first
        blankImageChunks =
            ImageUtil.splitBitmap(
                puzzleImage,
                tileDimen - BORDER_OFFSET,
                NUM_TILES,
                NUM_COLUMNS
            ).second
    }
    private fun displayPuzzle() {

        for ((position, tile) in puzzleState.withIndex()) {
            if (position == blankTilePos) {
                tileImages[blankTilePos].setImageBitmap(blankImageChunks[blankTilePos])
            } else {
                tileImages[position].setImageBitmap(imageChunks[tile])
            }
        }

        gvgPuzzle.adapter = TileAdapter(tileImages, tileDimen, tileDimen)
    }
    private fun displayBlankPuzzle() {
        for ((position, tile) in puzzleState.withIndex()) {
            if (position == blankTilePos) {
                tileImages[blankTilePos].setImageBitmap(blankImageChunks[blankTilePos])
            } else {
                tileImages[position].setImageBitmap(blankImageChunks[tile])
            }
        }
        gvgPuzzle.adapter = TileAdapter(tileImages, tileDimen, tileDimen)
    }
    private fun loadPuzzle(position: Int) {
        tvSuccess.visibility = View.GONE
        resetState()

        updatePuzzleImage(position)
        initChunks()
        displayPuzzle()
    }
    private fun updatePuzzleImage(position: Int) {
        puzzleImageIndex = position

        puzzleImage = ImageUtil.resizeToSquareBitmap(
            ImageUtil.drawableToBitmap(
                this@MainActivity, puzzleImageChoices[puzzleImageIndex].drawableId
            ),
            puzzleDimen,
            puzzleDimen
        )

        with(sp.edit()) {
            putInt(Key.KEY_PUZZLE_IMAGE.name, puzzleImageIndex)
            commit()
        }
    }

    //Moving Tiles
    private fun moveTile(direction: FlingDirection, position: Int) {
        var flag = false

        if (!isPuzzleGridFrozen) {
            if (MoveUtil.canMoveTile(direction, position, blankTilePos, NUM_COLUMNS)) {
                swap(puzzleState, position, blankTilePos)
                blankTilePos = position

                displayPuzzle()
                flag = updateGameStatus()

                if (numMoves == 1L) {
                    launchTimer()
                }
            }


            if (!flag) {
                tvSuccess.visibility = View.GONE
            }
        }
    }
    fun isSolved(puzzleState: ArrayList<Int>, goalPuzzleState: ArrayList<Int>): Boolean {
        return puzzleState == goalPuzzleState
    }
    private fun updateGameStatus(): Boolean {
        if (isGameInSession) {
            trackMove()

            if (isSolved(puzzleState, goalPuzzleState)) {

                timeTaken--

                endGame()

                return true
            }
        }

        return false
    }
    private fun trackMove() {
        numMoves++
        tvMoveNumber.text = numMoves.toString()
    }
    private fun launchTimer() {
        isTimerRunning = true
        timerHandler.post(object : Runnable {
            override fun run() {
                if (isTimerRunning) {
                    tvTimeTaken.text = Timer.displayTime(timeTaken++)
                    timerHandler.postDelayed(this, Timer.SECONDS_TO_MILLISECONDS.toLong())
                } else {
                    timerHandler.removeCallbacks(this)
                }
            }
        })
    }

    /************************************************************************/

    // Shuffling
    private fun setBtnShuffleAction() {
        btnShuffle.setOnClickListener {
            if (!isGameInSession) {
                shuffle()
            }
        }
    }
    private fun shuffle() {
        btnCamera.visibility=View.INVISIBLE
        btnShuffle.visibility= View.INVISIBLE
        tvMove.visibility=View.VISIBLE
        tvTime.visibility=View.VISIBLE
        spnPuzzle.visibility=View.INVISIBLE
        tvSuccess.visibility = View.GONE

        disableClickables()
        resetDisplayedStats()
        getValidShuffledState()
        displayBlankPuzzle()
        startShowingTiles()
    }
    private fun getValidShuffledState() {
        val shuffledState: StatePair =
            ShuffleUtil.getValidShuffledState(puzzleState, goalPuzzleState, BLANK_TILE_MARKER)

        puzzleState = shuffledState.puzzleState
        blankTilePos = shuffledState.blankTilePos
    }
    private fun updateComponents() {

        finishShuffling()

    }
    private fun finishShuffling() {
        isGameInSession = true

        enableClickables()
    }
    private fun disableClickables() {
        isPuzzleGridFrozen = true
        btnShuffle.isEnabled = false
        spnPuzzle.isEnabled = false
    }
    private fun enableClickables() {
        isPuzzleGridFrozen = false
        btnShuffle.isEnabled = true
    }
    private fun showTileAt(position: Int) {
        tileImages[position].setImageBitmap(imageChunks[puzzleState[position]])

        gvgPuzzle.adapter = TileAdapter(tileImages, tileDimen, tileDimen)
    }
    private fun startShowingTiles() {
        for (position in 0 until tileImages.size) {
            if (position != blankTilePos) {
                val delay: Long =
                    ((0..Animation.SHUFFLING_ANIMATION_UPPER_BOUND).random()
                            + Animation.SHUFFLING_ANIMATION_OFFSET).toLong()

                shuffleRunnable = ShuffleRunnable(shuffleHandler, position, NUM_TILES)
                shuffleScheduler.schedule(shuffleRunnable, delay, TimeUnit.MILLISECONDS)
            }
        }
    }
    /************************************************************************/

    //Uploading a Puzzle Image
    private fun loadPuzzle(imagePath: Uri?) {
        isGalleryImageChosen = true
        resetState()

        tvSuccess.visibility = View.GONE

        updatePuzzleImage(imagePath)
        initChunks()
        displayPuzzle()
    }
    private fun updatePuzzleImage(imagePath: Uri?) {
        puzzleImage = ImageUtil.resizeToSquareBitmap(
            BitmapFactory.decodeStream(
                contentResolver.openInputStream(imagePath!!)
            ),
            puzzleDimen,
            puzzleDimen
        )
    }
    /************************************************************************/

    //END OF THE GAME
    private fun endGame() {
        isGameInSession = false
        isTimerRunning = false

        displayScoreMessage()
    }

   private fun setBtnPlayAgainAction(){
       btnPlayAgain.setOnClickListener {
           val intent = intent
           finish()
           startActivity(intent)
       }
       }

    private fun displayScoreMessage() {
        tvSuccess.visibility = View.VISIBLE
        btnPlayAgain.visibility=View.VISIBLE
        if(timeTaken<=60){//1 minute or less
            tvSuccess.text ="EXCELLENTE!!\nSCORE: "+BestScore

        }else if(timeTaken>60 && timeTaken<=120){// more than 1 minute and equal or less than 2 minutes
            tvSuccess.text ="GOOD JOB!!\nSCORE: "+(BestScore-30)

        }else if(timeTaken>120 && timeTaken<=180){// more than 2 minutes and equal or less than 3 minutes
            tvSuccess.text ="NOT BAD.\nSCORE: "+(BestScore-60)

        }else if(timeTaken>180 && timeTaken<=240){ // more than 3 minutes and equal or less than 4 minutes
            tvSuccess.text ="TRY AGAIN NEXT TIME..\nSCORE: "+(BestScore-90)

        }else{//more than 4 minutes
            tvSuccess.text ="TRY AGAIN NEXT TIME..\nSCORE: "+(BestScore-95)

        }

        Handler(Looper.getMainLooper()).postDelayed({
            tvSuccess.visibility = View.GONE
        }, Animation.SUCCESS_DISPLAY.toLong())
    }

}