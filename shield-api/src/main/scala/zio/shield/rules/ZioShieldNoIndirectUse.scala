package zio.shield.rules

import scalafix.internal.scaluzzi.Disable.ContextTraverser
import scalafix.v1._
import zio.shield.flow._
import zio.shield.tag.Tag
import zio.shield.utils

import scala.meta._

class ZioShieldNoIndirectUse(cache: FlowCache)
    extends SemanticRule("ZioShieldNoIndirectUse") {

  override def fix(implicit doc: SemanticDocument): Patch = {

    def pureInterfaceOrImplementationParents(symbol: Symbol): List[String] =
      symbol.info match {
        case Some(info) =>
          info.signature match {
            case ClassSignature(_, parents, _, _) =>
              parents.collect {
                case TypeRef(_, s, _) =>
                  val isOkay = (for {
                    pure <- cache.searchTag(Tag.PureInterface)(s.value)
                    impl <- cache.searchTag(Tag.Implementaion)(s.value)
                  } yield pure || impl).getOrElse(false)
                  if (isOkay) Some(s.value) else None
              }.flatten
            case _ => List.empty
          }
        case _ => List.empty
      }

//    def checkName(name: Term.Name): Patch =
//      if (cache.searchTag(Tag.Effectful)(name.symbol.value).getOrElse(false)) {
//        Patch.lint(
//          Diagnostic(
//            "",
//            "effectful: ZIO effects usage outside of pure interface or implementation",
//            name.pos))
//      } else {
//        Patch.empty
//      }

    val noEffectful = ZioBlockDetector.lintFunction { s =>
      cache
        .searchTag(Tag.Effectful)(s.value)
        .getOrElse(false)
    } {
      case _ =>
        "effectful: ZIO effects usage outside of pure interface or implementation"
    }

    new ContextTraverser[Patch, Boolean](false)({
      // TODO currently there is no way to detect if the method overrides pure interface
      // we skip all the classes that extends pure interfaces, but that's wrong in some cases
      case (d: Defn.Class, false)
          if pureInterfaceOrImplementationParents(d.name.symbol).nonEmpty =>
        Right(true)
      case (d: Defn.Trait, false)
          if pureInterfaceOrImplementationParents(d.name.symbol).nonEmpty =>
        Right(true)
      case (t, false) if noEffectful.isDefinedAt(t) =>
        Left(noEffectful(t))
    }).result(doc.tree).asPatch
  }
}