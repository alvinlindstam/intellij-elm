package org.elm.ide.inspections

class ElmIncompletePatternInspectionTest : ElmInspectionsTestBase(ElmIncompletePatternInspection()) {

    fun `test no existing branch`() = checkFixByText("Add missing case branches", """
type Foo = Bar | Baz | Qux

foo : Foo -> ()
foo it =
    <error>case{-caret-}</error> it of
""", """
type Foo = Bar | Baz | Qux

foo : Foo -> ()
foo it =
    case it of
        Bar ->
            

        Baz ->
            

        Qux ->
            
""")

    fun `test one existing branch`() = checkFixByText("Add missing case branches", """
type Foo = Bar | Baz | Qux

foo : Foo -> ()
foo it =
    <error>case{-caret-}</error> it of
        Baz ->
            ()
""", """
type Foo = Bar | Baz | Qux

foo : Foo -> ()
foo it =
    case it of
        Baz ->
            ()

        Bar ->
            

        Qux ->
            
""")

    fun `test two existing branches`() = checkFixByText("Add missing case branches", """
type Foo = Bar | Baz | Qux

foo : Foo -> ()
foo it =
    <error>case{-caret-}</error> it of
        Baz ->
            ()

        Qux ->
            ()
""", """
type Foo = Bar | Baz | Qux

foo : Foo -> ()
foo it =
    case it of
        Baz ->
            ()

        Qux ->
            ()

        Bar ->
            
""")

    fun `test params`() = checkFixByText("Add missing case branches", """
type Foo = Foo
type alias BarBaz = Foo
type Maybe a = Just a | Nothing
type Msg a b
    = MsgOne Foo BarBaz
    | MsgTwo (Maybe (Maybe Foo))
    | MsgThree b a
    | MsgFour {x: ()}
    | MsgFive (x, y)
    | MsgSix (Msg () ())

foo : Msg a b -> ()
foo it =
    <error>case{-caret-}</error> it of
""", """
type Foo = Foo
type alias BarBaz = Foo
type Maybe a = Just a | Nothing
type Msg a b
    = MsgOne Foo BarBaz
    | MsgTwo (Maybe (Maybe Foo))
    | MsgThree b a
    | MsgFour {x: ()}
    | MsgFive (x, y)
    | MsgSix (Msg () ())

foo : Msg a b -> ()
foo it =
    case it of
        MsgOne foo barBaz ->
            

        MsgTwo maybe ->
            

        MsgThree b a ->
            

        MsgFour record ->
            

        MsgFive (x, y) ->
            

        MsgSix msg ->
            
""")

    fun `test one existing branch, wildcard pattern`() = checkFixByText("Add '_' branch", """
type Foo = Bar | Baz | Qux

foo : Foo -> ()
foo it =
    <error>case{-caret-}</error> it of
        Baz ->
            ()
""", """
type Foo = Bar | Baz | Qux

foo : Foo -> ()
foo it =
    case it of
        Baz ->
            ()

        _ ->
            
""")

    fun `test no leading whitespace`() = checkFixByText("Add missing case branches", """
type Foo = Bar

foo : Foo -> ()
foo it =<error>case{-caret-}</error> it of
""", """
type Foo = Bar

foo : Foo -> ()
foo it =case it of
        Bar ->
            
""")

    fun `test nesting in let`() = checkFixByText("Add missing case branches", """
type Foo = Bar

foo : Foo -> ()
foo it =
    let
        bar =
            <error>case{-caret-}</error> it of
    in
        ()
""", """
type Foo = Bar

foo : Foo -> ()
foo it =
    let
        bar =
            case it of
                Bar ->
                    
    in
        ()
""")

    fun `test nesting in case`() = checkFixByText("Add missing case branches", """
type Foo = Bar

foo : Foo -> ()
foo it =
    case () of
        () ->
            <error>case{-caret-}</error> it of
""", """
type Foo = Bar

foo : Foo -> ()
foo it =
    case () of
        () ->
            case it of
                Bar ->
                    
""")
}
