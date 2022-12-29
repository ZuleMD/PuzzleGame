package Puzzle.Helpimpl

class ShuffleUtil {

    companion object {

        fun getValidShuffledState(
            puzzleState: ArrayList<Int>,
            goalPuzzleState: ArrayList<Int>,
            blankTileMarker: Int
        ): StatePair {
             while (puzzleState == goalPuzzleState) {
                puzzleState.shuffle()

                if (!PuzzleUtil.isSolvable(puzzleState, blankTileMarker)) {
                    PuzzleUtil.swapTiles(puzzleState, blankTileMarker)
                }
            }

            return StatePair(puzzleState, puzzleState.indexOf(blankTileMarker))
        }
    }
}