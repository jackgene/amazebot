import Html exposing (..)
import Html.Attributes exposing (class, href, id, style, value)
import Html.Events exposing (onClick, onInput)
import Json.Decode exposing (decodeString, float, field, int, list, string)
import WebSocket

main : Program Request Model Msg
main =
  Html.programWithFlags
    { init = init
    , view = view
    , update = update
    , subscriptions = subscriptions
    }


-- Model
type alias Request =
  { secure: Bool
  , hostPath: String
  }
type alias Point =
  { topMm : Float
  , leftMm : Float
  }
type alias RobotPosition =
  { point : Point
  , orientationRad : Float
  }
type alias Wall =
  { topLeft: Point
  , bottomRightFromTopLeft: Point
  }
type alias Maze =
  { finish : Point
  , wallsHistory : List (List Wall)
  }
type alias Model =
  { request : Request
  , robotPosition : Maybe RobotPosition
  , maze : Maybe Maze
  , console : List String
  }

init : Request -> (Model, Cmd Msg)
init flags =
  (Model flags Nothing Nothing [], Cmd.none)


-- Update
type Msg
  = ChangeLanguage String
  | SaveAndRun
  | ClearConsole
  | ResetCode
  | ServerCommand String


pointJsonDecoder: String -> String -> Json.Decode.Decoder Point
pointJsonDecoder topField leftField =
  Json.Decode.map2 Point (field topField float) (field leftField float)


robotPositionJsonDecoder: Json.Decode.Decoder RobotPosition
robotPositionJsonDecoder =
  Json.Decode.map2 RobotPosition (pointJsonDecoder "t" "l") (field "o" float)


wallJsonDecoder: Json.Decode.Decoder Wall
wallJsonDecoder =
  Json.Decode.map2 Wall (pointJsonDecoder "t" "l") (pointJsonDecoder "h" "w")


mazeJsonDecoder : Json.Decode.Decoder Maze
mazeJsonDecoder =
  Json.Decode.map2 Maze (pointJsonDecoder "ft" "fl") (field "w" (list (list wallJsonDecoder)))


update : Msg -> Model -> (Model, Cmd Msg)
update msg model =
  case msg of
    ChangeLanguage lang ->
      ({ model | console = ("Change Language " ++ lang) :: model.console }, Cmd.none)
    SaveAndRun ->
      ({ model | console = "Save and Run" :: model.console }, Cmd.none)
    ResetCode ->
      ({ model | console = "Reset Code" :: model.console }, Cmd.none)
    ClearConsole ->
      ({ model | console = [] }, Cmd.none)
    ServerCommand json ->
      case decodeString (field "c" string) json of
        Ok "init" ->
          case (decodeString robotPositionJsonDecoder json) of
            Ok robotPosition ->
              ({ model | robotPosition = Just robotPosition }, Cmd.none)
            Err errorMsg ->
              Debug.log errorMsg (model, Cmd.none)
        Ok "maze" ->
          case (decodeString mazeJsonDecoder json) of
            Ok maze ->
              ({ model | maze = Just maze}, Cmd.none)
            Err errorMsg ->
              Debug.log errorMsg (model, Cmd.none)
        Ok _ ->
          Debug.log ("Unhandled command: " ++ json) (model, Cmd.none)
        Err _ ->
          Debug.log ("Error parsing JSON: " ++ json) (model, Cmd.none)


-- Subscriptions
subscriptions : Model -> Sub Msg
subscriptions {request} =
  WebSocket.listen
    ((if request.secure then "wss" else "ws") ++ "://" ++ request.hostPath ++ "/simulation")
    ServerCommand


-- View
robotRadiusMm : Float
robotRadiusMm = 173.5


finishZoneRadiusMm : Float
finishZoneRadiusMm = 250


mmToPixels : Float -> Float
mmToPixels mm =
  mm / 10


consoleLineView : String -> Html Msg
consoleLineView line =
  pre [] [text line]


wallView : Wall -> Html Msg
wallView wall =
  div
    [ class "wall"
    , style
        [ ("top", toString (mmToPixels wall.topLeft.topMm) ++ "px")
        , ("left", toString (mmToPixels wall.topLeft.leftMm) ++ "px")
        , ("height", toString (mmToPixels wall.bottomRightFromTopLeft.topMm) ++ "px")
        , ("width", toString (mmToPixels wall.bottomRightFromTopLeft.leftMm) ++ "px")
        ]
    ]
    []


mazeView : Maybe Maze -> List (Html Msg)
mazeView maybeMaze =
  case maybeMaze of
    Just maze ->
      div
        [ id "finish"
        , style [ ("top", toString (mmToPixels (maze.finish.topMm - finishZoneRadiusMm)) ++ "px")
                , ("left", toString (mmToPixels (maze.finish.leftMm - finishZoneRadiusMm)) ++ "px")
                ]
        ]
        [ text "move robot here" ]
      ::
      case (List.head maze.wallsHistory) of
        Just walls ->
          List.map wallView walls
        Nothing ->
          []
    Nothing ->
      []


robotView : Maybe RobotPosition -> List (Html Msg)
robotView maybeRobotPosition =
  case maybeRobotPosition of
    Just robotPosition ->
      [ div
          [ id "robot"
          , style [ ("top", toString (mmToPixels (robotPosition.point.topMm - robotRadiusMm)) ++ "px")
                  , ("left", toString (mmToPixels (robotPosition.point.leftMm - robotRadiusMm)) ++ "px")
                  , ("transform", "rotate(" ++ toString robotPosition.orientationRad ++ "rad)") -- animate from robotPosition.orientationRad - pi
                  ]
          ]
          []
      ]
    Nothing ->
      []

worldView : Maybe RobotPosition -> Maybe Maze -> Html Msg
worldView maybeRobotPosition maybeMaze =
  div [id "world"] ( mazeView maybeMaze ++ robotView maybeRobotPosition )


view : Model -> Html Msg
view model =
  div []
    [ ul [id "navigation"]
        [ li [] [a [href "/maze/level0"] [text "Level 0"]]
        , li [] [a [href "/maze/level1"] [text "Level 1"]]
        , li [] [a [href "/maze/level2"] [text "Level 2"]]
        , li [] [a [href "/maze/level3"] [text "Level 3"]]
        , li [] [a [href "/maze/level4"] [text "Level 4"]]
        , li [] [a [href "/maze/random"] [text "Random Maze"]]
        ]
    , div [id "input"]
        [ textarea [] []
        , br [] []
        , select [onInput ChangeLanguage]
            [ option
                [value "java"]
                [text "Java 8"]
            , option
                [value "py"]
                [text "Python 2.7"]
            ]
        , button [onClick SaveAndRun] [text "Save & Run"]
        , button [onClick ClearConsole] [text "Clear Console"]
        , button [onClick ResetCode] [text "Reset Code"]
        ]
    , div [id "output"] [ worldView model.robotPosition model.maze]
    , div [id "console"] (List.map consoleLineView (List.reverse model.console))
    ]
