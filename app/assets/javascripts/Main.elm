port module Main exposing (..)

import Html exposing (..)
import Html.Attributes exposing (class, href, id, selected, style, value)
import Html.Events exposing (onClick, onInput)
import Http
import Json.Decode exposing (decodeString, float, field, int, list, string)
import Json.Encode
import WebSocket

loadCodeTemplate : Request -> String -> Cmd Msg
loadCodeTemplate request language =
  Http.send
    ReceiveTemplatedSource
    (Http.getString ("//" ++ request.hostPath ++ "/template." ++ language))


webSocketUrl : Request -> String
webSocketUrl request =
  (if request.secure then "wss" else "ws") ++ "://" ++ request.hostPath ++ "/simulation"


port codeMirrorFromTextArea : (String, String) -> Cmd msg
port codeMirrorSetValue : String -> Cmd msg
port codeMirrorValueChanged : (String -> msg) -> Sub msg
port showMessage : String -> Cmd msg


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
  , robotPosition : Maybe RobotPosition
  , maze : Maybe Maze
  , console : List ConsoleMessage
  }


init : Request -> (Model, Cmd Msg)
init flags =
  let
    language : String
    language = "java"
  in
    ( Model flags language "" Nothing Nothing []
    , Cmd.batch
      [ loadCodeTemplate flags language
      , codeMirrorFromTextArea ("source", "text/x-java")
      ]
    )


-- Update
type Msg
  = SelectLanguage String
  | ChangeSource String
  | ReceiveTemplatedSource (Result Http.Error String)
  | SaveAndRun
  | ClearConsole
  | ResetCode
  | ServerCommand String


saveAndRunEncoder: Model -> Json.Encode.Value
saveAndRunEncoder model =
  Json.Encode.object
    [ ( "lang"
      , Json.Encode.string model.language
      )
    , ( "source"
      , Json.Encode.string model.source
      )
    ]


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
    SelectLanguage language ->
      ( { model | language = language }
      , loadCodeTemplate model.request language
      )
    ReceiveTemplatedSource result ->
      case result of
        Ok source ->
          ( { model | source = source }
          , codeMirrorSetValue source
          )
        Err errorMsg ->
          Debug.log ("Error obtaining code template: " ++ (toString errorMsg)) (model, Cmd.none)
    ChangeSource source ->
      ( { model | source = source }
      , Cmd.none
      )
    SaveAndRun ->
      ( model
      , WebSocket.send (webSocketUrl model.request) (Json.Encode.encode 0 (saveAndRunEncoder model))
      )
    ResetCode ->
      ( { model | console = (ConsoleMessage StdErr "TODO Reset Code") :: model.console }
      , Cmd.none
      )
    ClearConsole ->
      ( { model | console = [] }
      , Cmd.none
      )
    ServerCommand json ->
      Debug.log (toString json) (
      case decodeString (field "c" string) json of
        Ok "init" ->
          case (decodeString robotPositionJsonDecoder json) of
            Ok robotPosition ->
              ( { model | robotPosition = Just robotPosition }
              , Cmd.none
              )
            Err errorMsg ->
              Debug.log errorMsg (model, Cmd.none)
        Ok "maze" ->
          case (decodeString mazeJsonDecoder json) of
            Ok maze ->
              ( { model | maze = Just maze}
              , Cmd.none
              )
            Err errorMsg ->
              Debug.log errorMsg (model, Cmd.none)
        Ok "m" ->
          case (decodeString robotPositionJsonDecoder json) of
            Ok robotPosition ->
              ( { model | robotPosition = Just robotPosition }
              , Cmd.none
              )
            Err errorMsg ->
              Debug.log errorMsg (model, Cmd.none)
        Ok "msg" ->
          case decodeString (field "m" string) json of
            Ok message ->
              ( model
              , showMessage message
              )
            Err errorMsg ->
              Debug.log errorMsg (model, Cmd.none)
        Ok "log" ->
          case decodeString consoleMessageJsonDecoder json of
            Ok consoleMessage ->
              ( { model | console = consoleMessage :: model.console }
              , Cmd.none
              )
            _ ->
              Debug.log "Error parsing log json" (model, Cmd.none)
        Ok _ ->
          Debug.log ("Unhandled command: " ++ json) (model, Cmd.none)
        Err _ ->
          Debug.log ("Error parsing JSON: " ++ json) (model, Cmd.none)
      )

-- Subscriptions
subscriptions : Model -> Sub Msg
subscriptions {request} =
  Sub.batch
    [ WebSocket.listen (webSocketUrl request) ServerCommand
    , codeMirrorValueChanged ChangeSource
    ]


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
        StdOut -> []
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
        [ div []
          [ textarea [id "source"] [text model.source] ]
        , br [] []
        , select
            [onInput SelectLanguage]
            [ option
                [value "java", selected (model.language /= "python")]
                [text "Java 8"]
            , option
                [value "py", selected (model.language == "python")]
                [text "Python 2.7"]
            ]
        , button [onClick SaveAndRun] [text "Save & Run"]
        , button [onClick ClearConsole] [text "Clear Console"]
        , button [onClick ResetCode] [text "Reset Code"]
        ]
    , div [id "output"] [ worldView model.robotPosition model.maze]
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
