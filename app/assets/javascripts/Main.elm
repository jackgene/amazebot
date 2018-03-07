port module Main exposing (..)

-- TODO timer
-- TODO look into why simulation (line flashes) continue to come after collision

import Dom
import Dom.Scroll
import Html exposing (..)
import Html.Attributes exposing (class, href, id, selected, style, value)
import Html.Events exposing (onClick, onInput)
import Http
import Json.Decode exposing (decodeString, float, field, int, list, string)
import Json.Encode
import Task
import Time exposing (Time, millisecond, second)
import WebSocket


languageToMediaType : String -> String
languageToMediaType language =
  case language of
    "py" -> "text/x-python"
    _ -> "text/x-java"


loadCodeTemplate : Request -> String -> Cmd Msg
loadCodeTemplate request language =
  Http.send
    ReceivedTemplatedSource
    (Http.getString ("//" ++ request.host ++ request.pathname ++ "/template." ++ language))


webSocketUrl : Request -> String
webSocketUrl request =
  (if request.secure then "wss" else "ws") ++ "://" ++ request.host ++ request.pathname ++ "/simulation"


port localStorageSetItemCmd : (String, String) -> Cmd msg
port localStorageGetItemCmd : String -> Cmd msg
port localStorageGetItemSub : (Maybe String -> msg) -> Sub msg
port codeMirrorFromTextAreaCmd : (String, String) -> Cmd msg
port codeMirrorSetOptionCmd : (String, String) -> Cmd msg
port codeMirrorDocSetValueCmd : String -> Cmd msg
port codeMirrorDocValueChangedSub : (String -> msg) -> Sub msg
port codeMirrorFlashLineCmd : Int -> Cmd msg
port showMessageCmd : String -> Cmd msg


-- Model
type alias Request =
  { secure : Bool
  , host : String
  , pathname : String
  , initLang : String
  }
type alias Point =
  { topMm : Float
  , leftMm : Float
  }
type alias RobotPosition =
  { point : Point
  , orientationRad : Float
  }
type alias Robot =
  { position : RobotPosition
  , active : Bool
  }
type alias Wall =
  { topLeft: Point
  , bottomRightFromTopLeft: Point
  }
type alias Maze =
  { finish : Point
  , wallsHistory : List (List Wall)
  }
type ConsoleMessageType
  = StdOut
  | StdErr
type alias ConsoleMessage =
  { messageType : ConsoleMessageType
  , text : String
  }
type alias Model =
  { request : Request
  , language: String
  , source: String
  , startingPosition : Maybe RobotPosition
  , robot : Maybe Robot
  , maze : Maybe Maze
  , console : List ConsoleMessage
  }


init : Request -> (Model, Cmd Msg)
init request =
  ( Model request request.initLang "" Nothing Nothing Nothing []
  , Cmd.batch
    [ localStorageGetItemCmd (request.pathname ++ "/source." ++ request.initLang)
    , codeMirrorFromTextAreaCmd ("source", languageToMediaType request.initLang)
    ]
  )


-- Update
-- TODO think about message names
type Msg
  = ReceivedLocalStorageItem (Maybe String)
  | ReceivedTemplatedSource (Result Http.Error String)
  | AdvanceWallHistory Time
  | SelectLanguage String
  | ChangeSource String
  | SaveAndRun
  | ClearConsole
  | ResetCode
  | ServerCommand String
  | SendWebSocketKeepAlive Time
  | ConsoleScrolled (Result Dom.Error ())


saveAndRunEncoder : Model -> Json.Encode.Value
saveAndRunEncoder model =
  Json.Encode.object
    [ ( "lang"
      , Json.Encode.string model.language
      )
    , ( "source"
      , Json.Encode.string model.source
      )
    ]


pointJsonDecoder : String -> String -> Json.Decode.Decoder Point
pointJsonDecoder topField leftField =
  Json.Decode.map2 Point (field topField float) (field leftField float)


robotPositionJsonDecoder : Json.Decode.Decoder RobotPosition
robotPositionJsonDecoder =
  Json.Decode.map2 RobotPosition (pointJsonDecoder "t" "l") (field "o" float)


wallJsonDecoder : Json.Decode.Decoder Wall
wallJsonDecoder =
  Json.Decode.map2 Wall (pointJsonDecoder "t" "l") (pointJsonDecoder "h" "w")


mazeJsonDecoder : Json.Decode.Decoder Maze
mazeJsonDecoder =
  Json.Decode.map2 Maze (pointJsonDecoder "ft" "fl") (field "w" (list (list wallJsonDecoder)))


consoleMessageJsonDecoder : Json.Decode.Decoder ConsoleMessage
consoleMessageJsonDecoder =
  Json.Decode.map2
    ( \m -> \t ->
      ConsoleMessage
        ( case t of
          "o" -> StdOut
          _ -> StdErr
        )
        m
    )
    (field "m" string)
    (field "t" string)


update : Msg -> Model -> (Model, Cmd Msg)
update msg model =
  case msg of
    ReceivedLocalStorageItem maybeItem ->
      case maybeItem of
        Just source ->
          ( { model | source = source }
          , codeMirrorDocSetValueCmd source
          )
        Nothing ->
          (model, loadCodeTemplate model.request model.language)
    ReceivedTemplatedSource result ->
      case result of
        Ok source ->
          ( { model | source = source }
          , codeMirrorDocSetValueCmd source
          )
        Err errorMsg ->
          Debug.log ("Error obtaining code template: " ++ (toString errorMsg)) (model, Cmd.none)
    AdvanceWallHistory _ ->
      let
        updatedMaze : Maybe Maze
        updatedMaze =
          Maybe.map
            ( \maze ->
              let
                updatedWallsHistory : List (List Wall)
                updatedWallsHistory =
                  case maze.wallsHistory of
                    _ :: curWallSet :: nextWallSets ->
                      curWallSet :: nextWallSets
                    onlyOrNoWallSet ->
                      onlyOrNoWallSet
              in
                { maze | wallsHistory = updatedWallsHistory }
            )
            model.maze
      in
        ( { model | maze = updatedMaze }
        , Cmd.none
        )
    SelectLanguage language ->
      ( { model | language = language }
      , Cmd.batch
        [ codeMirrorSetOptionCmd
          ( "mode"
          , languageToMediaType language
          )
        , localStorageSetItemCmd ("lang", language)
        , localStorageGetItemCmd (model.request.pathname ++ "/source." ++ model.language)
        ]
      )
    ChangeSource source ->
      ( { model | source = source }
      , Cmd.none
      )
    SaveAndRun ->
      ( { model | robot = Maybe.map (\robotPos -> Robot robotPos False) model.startingPosition }
      , Cmd.batch
        [ localStorageSetItemCmd ("lang", model.language)
        , localStorageSetItemCmd ((model.request.pathname ++ "/source." ++ model.language), model.source)
        , WebSocket.send (webSocketUrl model.request) (Json.Encode.encode 0 (saveAndRunEncoder model))
        ]
      )
    ResetCode ->
      ( { model | source = "" }
      , codeMirrorDocSetValueCmd ""
      )
    ClearConsole ->
      ( { model | console = [] }
      , Cmd.none
      )
    ServerCommand json ->
      case decodeString (field "c" string) json of
        Ok "maze" ->
          case (decodeString mazeJsonDecoder json) of
            Ok maze ->
              ( { model
                | maze = Just {maze | wallsHistory = List.reverse maze.wallsHistory}
                }
              , Cmd.none
              )
            Err errorMsg ->
              Debug.log ("Error parsing draw maze command: " ++ errorMsg) (model, Cmd.none)
        Ok "init" ->
          case (decodeString robotPositionJsonDecoder json) of
            Ok robotPosition ->
              ( { model
                | robot = Just (Robot robotPosition False)
                , startingPosition = Just (Maybe.withDefault robotPosition model.startingPosition)
                }
              , Cmd.none
              )
            Err errorMsg ->
              Debug.log ("Error parsing robot initialization command: " ++ errorMsg) (model, Cmd.none)
        Ok "m" ->
          case (decodeString robotPositionJsonDecoder json) of
            Ok robotPosition ->
              ( {model | robot = Just (Robot robotPosition True)}
              , Cmd.none
              )
            Err errorMsg ->
              Debug.log ("Error parsing move robot command: " ++ errorMsg) (model, Cmd.none)
        Ok "msg" ->
          case decodeString (field "m" string) json of
            Ok message ->
              ( model
              , showMessageCmd message
              )
            Err errorMsg ->
              Debug.log ("Error parsing message alert command: " ++ errorMsg) (model, Cmd.none)
        Ok "log" ->
          case decodeString consoleMessageJsonDecoder json of
            Ok consoleMessage ->
              ( { model | console = consoleMessage :: model.console }
              , Task.attempt ConsoleScrolled (Dom.Scroll.toBottom "console")
              )
            Err errorMsg ->
              Debug.log ("Error parsing console log command: " ++ errorMsg) (model, Cmd.none)
        Ok "l" ->
          case decodeString (field "l" int) json of
            Ok line ->
              ( model
              , codeMirrorFlashLineCmd line
              )
            Err errorMsg ->
              Debug.log ("Error parsing line execution command: " ++ errorMsg) (model, Cmd.none)
        Ok _ ->
          Debug.log ("Unhandled command: " ++ json) (model, Cmd.none)
        Err errorMsg ->
          Debug.log ("Error parsing WebSocket command JSON: " ++ errorMsg) (model, Cmd.none)
    SendWebSocketKeepAlive _ ->
      ( model
      , WebSocket.send (webSocketUrl model.request) "{}"
      )
    ConsoleScrolled _ ->
      ( model, Cmd.none ) -- No-op


-- Subscriptions
mapDrawInProgress : Maybe Maze -> Bool
mapDrawInProgress maybeMaze =
  case maybeMaze of
    Just maze -> 1 < (List.length maze.wallsHistory)
    Nothing -> False


subscriptions : Model -> Sub Msg
subscriptions model =
  Sub.batch
    ( [ codeMirrorDocValueChangedSub ChangeSource
      , localStorageGetItemSub ReceivedLocalStorageItem
      , Time.every (45 * second) SendWebSocketKeepAlive
      , WebSocket.listen (webSocketUrl model.request) ServerCommand
      ] ++
      if not (mapDrawInProgress model.maze) then []
      else [Time.every (50 * millisecond) AdvanceWallHistory]
    )


-- View
robotRadiusMm : Float
robotRadiusMm = 173.5


finishZoneRadiusMm : Float
finishZoneRadiusMm = 250


mmToPixels : Float -> Float
mmToPixels mm =
  mm / 10


consoleMessageView : ConsoleMessage -> Html Msg
consoleMessageView message =
  pre
    ( case message.messageType of
        StdOut -> [ class "stdout" ]
        StdErr -> [ class "stderr" ]
    )
    [text message.text]


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


robotView : Maybe Robot -> List (Html Msg)
robotView maybeRobot =
  case maybeRobot of
    Just {position, active} ->
      [ div
          [ id "robot"
          , style
            [ ("top", toString (mmToPixels (position.point.topMm - robotRadiusMm)) ++ "px")
            , ("left", toString (mmToPixels (position.point.leftMm - robotRadiusMm)) ++ "px")
            , ("transform", "rotate(" ++ toString position.orientationRad ++ "rad)")
            , ("transition", if active then "all 400ms linear" else "none")
            ]
          ]
          []
      ]
    Nothing ->
      []


worldView : Maybe Robot -> Maybe Maze -> Html Msg
worldView maybeRobot maybeMaze =
  div [id "world"] (robotView maybeRobot ++ mazeView maybeMaze)


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
        [ div []
          [textarea [id "source"] [text model.source]]
        , br [] []
        , select
            [onInput SelectLanguage]
            [ option
                [value "java", selected (model.language /= "py")]
                [text "Java 8"]
            , option
                [value "py", selected (model.language == "py")]
                [text "Python 2.7"]
            ]
        , button [onClick SaveAndRun] [text "Save & Run"]
        , button [onClick ClearConsole] [text "Clear Console"]
        , button [onClick ResetCode] [text "Reset Code"]
        ]
    , div [id "output"] [worldView model.robot model.maze]
    , div [id "console"] (List.map consoleMessageView (List.reverse model.console))
    ]


main : Program Request Model Msg
main =
  Html.programWithFlags
    { init = init
    , view = view
    , update = update
    , subscriptions = subscriptions
    }
